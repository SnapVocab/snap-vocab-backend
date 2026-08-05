"""Florence-2 model loading and open-vocabulary object detection.

Ported from the `object_scan_florence2` research notebook (cells 4 + 8), trimmed
to the "product mode" behavior (see cell 25): OD + tiled OD + self-grounding +
base/extra vocabulary grounding. Evaluation-only ablations were dropped.
"""
from __future__ import annotations

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

PSEUDO_SCORE = {"od": 0.90, "grounding": 0.85, "self": 0.80, "dense": 0.75, "base": 0.70}


def _fixed_get_imports(filename):
    """flash_attn isn't installed/needed for CPU/most GPU setups; strip the import."""
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

    @torch.no_grad()
    def run(self, image: Image.Image, task_prompt: str, text_input: str | None = None) -> Any:
        prompt = task_prompt if text_input is None else task_prompt + text_input
        inputs = self.processor(text=prompt, images=image, return_tensors="pt").to(
            self.device, self.torch_dtype,
        )
        generated_ids = self.model.generate(
            input_ids=inputs["input_ids"],
            pixel_values=inputs["pixel_values"],
            max_new_tokens=self.settings.florence_max_new_tokens,
            num_beams=self.settings.florence_num_beams,
            do_sample=False,
        )
        text = self.processor.batch_decode(generated_ids, skip_special_tokens=False)[0]
        return self.processor.post_process_generation(
            text, task=task_prompt, image_size=(image.width, image.height),
        )[task_prompt]

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
            for (tx1, ty1, tx2, ty2) in tiles:
                od_t = self.run(image.crop((tx1, ty1, tx2, ty2)), "<OD>")
                for box, raw in zip(od_t.get("bboxes", []), od_t.get("labels", [])):
                    x1, y1, x2, y2 = (float(v) for v in box)
                    tw, th = tx2 - tx1, ty2 - ty1
                    # drop boxes touching the tile's INNER edge (object cut off by the tile);
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
            _log_stage("  1b.OD tiled", time.perf_counter() - tiles_started, "4 calls")

        # ---- 1c. Self-grounding: detailed caption -> ground that same caption ----
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
            _log_stage("  1c.self-grounding", time.perf_counter() - self_started, "2 calls")

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
            n_chunks = (len(todo) + 11) // 12
            for k in range(0, len(todo), 12):
                chunk = todo[k:k + 12]
                text = ". ".join(chunk) + "."
                gr = self.run(image, "<CAPTION_TO_PHRASE_GROUNDING>", text_input=text)
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
            _log_stage("  3.base-vocab", time.perf_counter() - base_started, f"{n_chunks} calls")

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
            iou = compute_iou(survivors[i]["box"], survivors[j]["box"])
            same = survivors[i]["label"] == survivors[j]["label"]
            related = same or labels_match(survivors[j]["label"], survivors[i]["label"]) \
                or labels_match(survivors[i]["label"], survivors[j]["label"])
            if (same and iou >= same_label_iou) or (related and iou >= related_label_iou):
                suppressed[j] = True
    return kept
