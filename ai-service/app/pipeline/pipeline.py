"""End-to-end orchestration: Florence-2 detect -> dedup -> CLIP-gate -> SAM crop.

Adapted from `process_and_crop_flashcards` in the research notebook (cell 10),
with the Colab/matplotlib/eval-only pieces removed and PNG crops returned as
in-memory bytes instead of being written to disk.
"""
from __future__ import annotations

import io

import numpy as np
from PIL import Image

from app.config import Settings
from app.pipeline.clip import ClipEngine
from app.pipeline.florence import FlorenceEngine, dedup_detections
from app.pipeline.labels import is_valid_dictionary_word
from app.pipeline.sam import SamEngine


class DetectionResult:
    __slots__ = ("label", "score", "source", "box", "crop_png")

    def __init__(self, label: str, score: float, source: str, box: list[float], crop_png: bytes):
        self.label = label
        self.score = score
        self.source = source
        self.box = box
        self.crop_png = crop_png


def _crop_to_png_bytes(image_np: np.ndarray, mask: np.ndarray, box: list[float], padding: int) -> bytes | None:
    height, width = image_np.shape[:2]
    ys, xs = np.where(mask)
    if ys.size == 0 or xs.size == 0:
        return None
    mx1 = max(0, int(xs.min()) - padding)
    my1 = max(0, int(ys.min()) - padding)
    mx2 = min(width, int(xs.max()) + 1 + padding)
    my2 = min(height, int(ys.max()) + 1 + padding)
    if mx2 <= mx1 or my2 <= my1:
        return None
    rgba = np.dstack([image_np, (mask.astype(np.uint8) * 255)])
    crop = Image.fromarray(rgba[my1:my2, mx1:mx2], mode="RGBA")
    buf = io.BytesIO()
    crop.save(buf, format="PNG")
    return buf.getvalue()


def process_image(
    settings: Settings,
    florence: FlorenceEngine,
    sam: SamEngine,
    clip: ClipEngine,
    raw_image: Image.Image,
) -> list[DetectionResult]:
    raw_image = raw_image.convert("RGB")
    image_np = np.array(raw_image)

    # 1. Florence-2: propose boxes + open-vocab labels
    detections = florence.detect(raw_image)

    # 2. Dedup between <OD> / tiled OD / self-grounding / dense / base-vocab
    detections = dedup_detections(detections)

    # 3. CLIP gates the base-vocabulary detections (the only source prone to hallucination)
    base_dets = [d for d in detections if d.get("source") == "base"]
    if base_dets:
        others = [d for d in detections if d.get("source") != "base"]
        kept, _dropped = clip.verify(raw_image, base_dets, settings.clip_base_threshold)
        detections = others + kept

    # 4. Flashcard mode: keep only the top-scoring box per label
    if settings.max_cards_per_label:
        by_label: dict[str, list[dict]] = {}
        for d in sorted(detections, key=lambda x: -x["score"]):
            by_label.setdefault(d["label"], []).append(d)
        detections = [d for lst in by_label.values() for d in lst[: settings.max_cards_per_label]]

    # 5. SAM verifies + segments each box, crops a transparent-background flashcard image
    sam.set_image(image_np)
    results: list[DetectionResult] = []
    for d in detections:
        if settings.require_dictionary_labels and not is_valid_dictionary_word(d["label"].split()[-1]):
            continue

        x1, y1, x2, y2 = d["box"]
        mask, _iou_pred, _stability = sam.segment_box(d["box"])
        mask_area = int(mask.sum())
        box_area = max(1.0, (x2 - x1) * (y2 - y1))
        if mask_area < settings.sam_min_mask_area_px or (mask_area / box_area) < settings.sam_min_mask_box_ratio:
            continue

        png_bytes = _crop_to_png_bytes(image_np, mask, d["box"], settings.sam_crop_padding)
        if png_bytes is None:
            continue

        results.append(DetectionResult(
            label=d["label"], score=d["score"], source=d["source"], box=d["box"], crop_png=png_bytes,
        ))

    return results
