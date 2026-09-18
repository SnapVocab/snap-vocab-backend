"""Florence-2 model loading and open-vocabulary object detection.

Ported from the `object_scan_florence2` research notebook (cells 4 + 8), trimmed
to the "product mode" behavior (see cell 25): OD + tiled OD + self-grounding +
base/extra vocabulary grounding. Evaluation-only ablations were dropped.

Stages that issue many calls sharing one task prompt (tiled OD, background
vocabulary) go through `run_many`, which feeds them to `generate` in batches.
Decoding is bandwidth-bound, so a batch of N prompts costs far less than N
sequential calls on GPU -- that is where most of the per-image latency was.
"""
from __future__ import annotations

import logging
import time
from typing import Any
from unittest.mock import patch

import torch
from PIL import Image
from transformers import AutoModelForCausalLM, AutoProcessor
from transformers.dynamic_module_utils import get_imports

from app.config import Settings
from app.pipeline.labels import BASE_VOCAB, EXTRA_HOUSEHOLD_VOCAB, clean_label, compute_iou, labels_match
from app.pipeline.timing import log_stage as _log_stage, stage

logger = logging.getLogger("ai-service")

PSEUDO_SCORE = {"od": 0.90, "grounding": 0.85, "self": 0.80, "dense": 0.75, "base": 0.70}


def _fixed_get_imports(filename):
    """flash_attn is not installed/needed for CPU/most GPU setups; strip the import."""
    imports = get_imports(filename)
    if not str(filename).endswith("modeling_florence2.py"):
        return imports
    return [i for i in imports if i != "flash_attn"]


class FlorenceEngine:
    def __init__(self, settings: Settings, device: str, torch_dtype: torch.dtype):
        self.settings = settings
        self.device = device
        self.torch_dtype = torch_dtype
        self.model = None
        self.processor = None

    def load(self) -> None:
        with patch("transformers.dynamic_module_utils.get_imports", _fixed_get_imports):
            self.model = AutoModelForCausalLM.from_pretrained(
                self.settings.florence_model_id,
                torch_dtype=self.torch_dtype,
                trust_remote_code=True,
                attn_implementation="sdpa",
            ).to(self.device).eval()
            self.processor = AutoProcessor.from_pretrained(
                self.settings.florence_model_id, trust_remote_code=True,
            )

    @property
    def loaded(self) -> bool:
        return self.model is not None

    def warmup(self) -> None:
        """Force CUDA kernel init / cuDNN autotuning now instead of on request #1."""
        if not self.loaded:
            return
        started = time.perf_counter()
        try:
            self.run(Image.new("RGB", (64, 64), (127, 127, 127)), "<OD>")
        except Exception as exc:  # a failed warm-up must not stop the service
            logger.warning("Warm-up pass failed (harmless, first request pays the cost): %s", exc)
            return
        logger.info("Warm-up pass done in %.2fs", time.perf_counter() - started)

    # ---- generation ----

    @torch.no_grad()
    def _generate(self, images: list[Image.Image], prompts: list[str], task_prompt: str) -> list[Any]:
        """One generate() call over a batch of (image, prompt) pairs of EQUAL prompt length.

        Two constraints come straight from Florence-2 remote code and drive the
        batching strategy in `run_many` (see modeling_florence2.py):

        1. Its `generate` merges image features with the text embeds and builds a
           matching attention mask, then throws that mask away and forwards
           `**kwargs` to the language model. Passing our own `attention_mask`
           makes the text-length mask collide with the merged sequence length
           (e.g. 42 vs 577+42) and generation dies. So we never pass one.
        2. `_merge_input_ids_with_image_features` hardcodes the text mask to
           `torch.ones(...)`. Nothing is ever masked out, so a padded batch would
           attend to PAD tokens as if they were real words -- wrong results, no
           error. Batching is therefore only safe when no padding is added.
        """
        inputs = self.processor(text=prompts, images=images, return_tensors="pt").to(
            self.device, self.torch_dtype,
        )
        generated_ids = self.model.generate(
            input_ids=inputs["input_ids"],
            pixel_values=inputs["pixel_values"],
            max_new_tokens=self.settings.florence_max_new_tokens,
            num_beams=self.settings.florence_num_beams,
            do_sample=False,
        )
        texts = self.processor.batch_decode(generated_ids, skip_special_tokens=False)
        return [
            self.processor.post_process_generation(
                text, task=task_prompt, image_size=(image.width, image.height),
            )[task_prompt]
            for text, image in zip(texts, images)
        ]

    def run_many(
        self,
        images: list[Image.Image],
        task_prompt: str,
        text_inputs: list[str] | None = None,
    ) -> list[Any]:
        """Run one task prompt over many images/texts, batching what is safe to batch.

        Only prompts that tokenize to the SAME length share a generate() call --
        see `_generate` for why padding is not an option. Stages that repeat one
        fixed prompt (tiled OD) batch completely; stages with variable text
        (background vocabulary) batch only within equal-length groups, which is
        why they still issue several calls.
        """
        if not images:
            return []
        prompts = (
            [task_prompt] * len(images)
            if text_inputs is None
            else [task_prompt + t for t in text_inputs]
        )

        size = max(1, self.settings.florence_batch_size)
        lengths = [len(ids) for ids in self.processor.tokenizer(prompts)["input_ids"]]

        # Bucket indices by prompt length, then cut each bucket into batches.
        buckets: dict[int, list[int]] = {}
        for index, length in enumerate(lengths):
            buckets.setdefault(length, []).append(index)

        results: list[Any | None] = [None] * len(images)
        for indices in buckets.values():
            for start in range(0, len(indices), size):
                group = indices[start:start + size]
                batch_images = [images[i] for i in group]
                batch_prompts = [prompts[i] for i in group]
                try:
                    outputs = self._generate(batch_images, batch_prompts, task_prompt)
                except Exception as exc:
                    if len(group) == 1:
                        raise
                    logger.warning(
                        "Batched generate failed for %d prompts (%s); retrying one at a time. "
                        "Lower FLORENCE_BATCH_SIZE to avoid this.", len(group), exc,
                    )
                    outputs = [
                        self._generate([image], [prompt], task_prompt)[0]
                        for image, prompt in zip(batch_images, batch_prompts)
                    ]
                for index, output in zip(group, outputs):
                    results[index] = output
        return results

    def run(self, image: Image.Image, task_prompt: str, text_input: str | None = None) -> Any:
        """Single-prompt convenience wrapper around `run_many`."""
        return self.run_many(
            [image], task_prompt, None if text_input is None else [text_input],
        )[0]

    # ---- detection ----

    def detect(self, image: Image.Image) -> list[dict]:
        """Return list[{"label", "score", "box", "source"}] with pixel-space boxes [x1,y1,x2,y2]."""
        settings = self.settings
        detections: list[dict] = []
        strict = settings.strict_dictionary_filter

        # ---- 1. <OD> ----
        with stage("  1a.OD", "1 call"):
            od = self.run(image, "<OD>")
        for box, raw in zip(od.get("bboxes", []), od.get("labels", [])):
            label = clean_label(raw, from_caption=False, strict_dictionary_filter=strict)
            if label:
                detections.append({
                    "label": label, "score": PSEUDO_SCORE["od"],
                    "box": [float(v) for v in box], "source": "od",
                })

        # ---- 1b. <OD> on 4 overlapping tiles -- catches small objects <OD> misses whole-image ----
        if settings.enable_tiled_od:
            width, height = image.width, image.height
            tiles = [
                (0, 0, int(0.6 * width), int(0.6 * height)),
                (int(0.4 * width), 0, width, int(0.6 * height)),
                (0, int(0.4 * height), int(0.6 * width), height),
                (int(0.4 * width), int(0.4 * height), width, height),
            ]
            margin = 8
            tiles_started = time.perf_counter()
            crops = [image.crop(t) for t in tiles]
            # One batched call for all 4 tiles instead of 4 sequential ones.
            tile_results = self.run_many(crops, "<OD>")
            for (tx1, ty1, tx2, ty2), od_t in zip(tiles, tile_results):
                for box, raw in zip(od_t.get("bboxes", []), od_t.get("labels", [])):
                    x1, y1, x2, y2 = (float(v) for v in box)
                    tw, th = tx2 - tx1, ty2 - ty1
                    # drop boxes touching the tile INNER edge (object cut off by the tile);
                    # edges that coincide with the actual image border are fine.
                    if ((x1 < margin and tx1 > 0) or (y1 < margin and ty1 > 0)
                            or (x2 > tw - margin and tx2 < width) or (y2 > th - margin and ty2 < height)):
                        continue
                    label = clean_label(raw, from_caption=False, strict_dictionary_filter=strict)
                    if label:
                        detections.append({
                            "label": label, "score": PSEUDO_SCORE["od"] - 0.02,
                            "box": [x1 + tx1, y1 + ty1, x2 + tx1, y2 + ty1],
                            "source": "od_tile",
                        })
            _log_stage("  1b.OD tiled", time.perf_counter() - tiles_started, "4 prompts, batched")

        # ---- 1c. Self-grounding: detailed caption -> ground that same caption ----
        # Inherently sequential: the second call text input is the first call output.
        if settings.enable_self_grounding:
            self_started = time.perf_counter()
            try:
                detailed_cap = self.run(image, "<MORE_DETAILED_CAPTION>")
                if isinstance(detailed_cap, str) and detailed_cap.strip():
                    gr_self = self.run(
                        image, "<CAPTION_TO_PHRASE_GROUNDING>", text_input=detailed_cap.strip(),
                    )
                    for box, phrase in zip(gr_self.get("bboxes", []), gr_self.get("labels", [])):
                        label = clean_label(phrase, from_caption=True, strict_dictionary_filter=strict)
                        if label:
                            detections.append({
                                "label": label, "score": PSEUDO_SCORE["self"],
                                "box": [float(v) for v in box], "source": "self",
                            })
            except Exception:
                pass
            _log_stage("  1c.self-grounding", time.perf_counter() - self_started, "2 calls (sequential)")

        # ---- 2. <DENSE_REGION_CAPTION> ----
        if settings.enable_dense_caption:
            with stage("  2.dense-caption", "1 call"):
                dense = self.run(image, "<DENSE_REGION_CAPTION>")
            for box, cap in zip(dense.get("bboxes", []), dense.get("labels", [])):
                label = clean_label(cap, from_caption=True, strict_dictionary_filter=strict)
                if label:
                    detections.append({
                        "label": label, "score": PSEUDO_SCORE["dense"],
                        "box": [float(v) for v in box], "source": "dense",
                    })

        # ---- 3. Background vocabulary coverage (BASE_VOCAB [+ EXTRA_HOUSEHOLD_VOCAB]) ----
        if settings.enable_base_vocab:
            img_area = float(image.width * image.height)
            existing = [d["label"] for d in detections]
            query_vocab = list(BASE_VOCAB) + (EXTRA_HOUSEHOLD_VOCAB if settings.enable_extra_vocab else [])
            todo = [
                w for w in query_vocab
                if not any(labels_match(w, e) or labels_match(e, w) for e in existing)
            ]
            base_started = time.perf_counter()
            chunks = [todo[k:k + 12] for k in range(0, len(todo), 12)]
            # All chunks share the same task prompt and image, so they batch.
            chunk_results = self.run_many(
                [image] * len(chunks),
                "<CAPTION_TO_PHRASE_GROUNDING>",
                text_inputs=[". ".join(c) + "." for c in chunks],
            )
            for chunk, gr in zip(chunks, chunk_results):
                for box, raw in zip(gr.get("bboxes", []), gr.get("labels", [])):
                    label = (raw or "").strip().lower()
                    if not label or label not in chunk:
                        continue  # only accept exact vocab words, drop odd merged phrases
                    x1, y1, x2, y2 = (float(v) for v in box)
                    if (x2 - x1) * (y2 - y1) > 0.85 * img_area:
                        continue  # near-fullscreen box = grounding hallucination signal
                    detections.append({
                        "label": label, "score": PSEUDO_SCORE["base"],
                        "box": [x1, y1, x2, y2], "source": "base",
                    })
            _log_stage("  3.base-vocab", time.perf_counter() - base_started, f"{len(chunks)} prompts, batched")

        return detections


def dedup_detections(
    detections: list[dict], same_label_iou: float = 0.5, related_label_iou: float = 0.7,
) -> list[dict]:
    """Two-tier NMS: same label (IoU>=0.5) and "related" label per labels_match (IoU>=0.7).
    Prefers higher-score sources (<OD> > grounding > dense)."""
    survivors = sorted(detections, key=lambda d: -d["score"])
    kept: list[dict] = []
    suppressed = [False] * len(survivors)
    for i in range(len(survivors)):
        if suppressed[i]:
            continue
        kept.append(survivors[i])
        for j in range(i + 1, len(survivors)):
            if suppressed[j]:
                continue
            # IoU is cheap and rejects most pairs; the WordNet walk behind
            # labels_match is not, so only reach for it once IoU clears the bar.
            iou = compute_iou(survivors[i]["box"], survivors[j]["box"])
            if iou < min(same_label_iou, related_label_iou):
                continue
            same = survivors[i]["label"] == survivors[j]["label"]
            if same and iou >= same_label_iou:
                suppressed[j] = True
                continue
            if iou >= related_label_iou and (
                labels_match(survivors[j]["label"], survivors[i]["label"])
                or labels_match(survivors[i]["label"], survivors[j]["label"])
            ):
                suppressed[j] = True
    return kept
