"""Production-lite inference: whole-image Florence-2 OD plus dense captions."""
from __future__ import annotations

import logging
import math
import time
from dataclasses import dataclass
from typing import Any, Literal

from PIL import Image

from app.config import Settings
from app.pipeline.florence import FlorenceEngine
from app.pipeline.labels import compute_iou, extract_concrete_nouns

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class LiteDetectionResult:
    label: str
    source: Literal["od", "dense"]
    box: tuple[float, float, float, float]


def _normalize_label(raw_label: Any) -> str | None:
    if not isinstance(raw_label, str):
        return None
    label = raw_label.strip().lower()
    return label or None


def _clamp_box(raw_box: Any, width: int, height: int) -> list[float] | None:
    try:
        if len(raw_box) != 4:
            return None
        if any(isinstance(value, bool) for value in raw_box):
            return None
        x1, y1, x2, y2 = (float(value) for value in raw_box)
    except (TypeError, ValueError, OverflowError):
        return None
    if not all(math.isfinite(value) for value in (x1, y1, x2, y2)):
        return None

    x1 = min(max(x1, 0.0), float(width))
    y1 = min(max(y1, 0.0), float(height))
    x2 = min(max(x2, 0.0), float(width))
    y2 = min(max(y2, 0.0), float(height))
    if x2 <= x1 or y2 <= y1:
        return None
    return [x1, y1, x2, y2]


def _same_label_nms(detections: list[dict], threshold: float) -> list[dict]:
    """Stable class-aware NMS; spatially separate objects with one label survive."""
    kept: list[dict] = []
    for candidate in detections:
        duplicate = any(
            candidate["label"] == previous["label"]
            and compute_iou(candidate["box"], previous["box"]) >= threshold
            for previous in kept
        )
        if not duplicate:
            kept.append(candidate)
    return kept


class LiteDetectionPipeline:
    def __init__(self, settings: Settings, florence: FlorenceEngine) -> None:
        self.settings = settings
        self.florence = florence

    def process(self, raw_image: Image.Image) -> list[LiteDetectionResult]:
        total_started = time.perf_counter()
        image = raw_image.convert("RGB")
        width, height = image.size

        od_started = time.perf_counter()
        od_output = self.florence.run(image, "<OD>")
        od_ms = (time.perf_counter() - od_started) * 1000

        dense_started = time.perf_counter()
        dense_output = self.florence.run(image, "<DENSE_REGION_CAPTION>")
        dense_ms = (time.perf_counter() - dense_started) * 1000

        postprocess_started = time.perf_counter()
        od_detections: list[dict] = []
        if not isinstance(od_output, dict):
            od_output = {}
        raw_boxes = od_output.get("bboxes") or []
        raw_labels = od_output.get("labels") or []
        for raw_box, raw_label in zip(raw_boxes, raw_labels):
            label = _normalize_label(raw_label)
            box = _clamp_box(raw_box, width, height)
            if label is not None and box is not None:
                od_detections.append({"label": label, "source": "od", "box": box})

        dense_detections: list[dict] = []
        if not isinstance(dense_output, dict):
            dense_output = {}
        raw_boxes = dense_output.get("bboxes") or []
        raw_captions = dense_output.get("labels") or []
        for raw_box, caption in zip(raw_boxes, raw_captions):
            box = _clamp_box(raw_box, width, height)
            if box is None:
                continue
            for label in extract_concrete_nouns(caption):
                dense_detections.append({"label": label, "source": "dense", "box": box})

        # OD is deliberately first: stable NMS therefore prefers it over a dense
        # detection with the same normalized label and overlapping box.
        detections = _same_label_nms(
            od_detections + dense_detections,
            self.settings.lite_nms_iou_threshold,
        )
        detections = detections[: self.settings.lite_max_detections]
        results = [
            LiteDetectionResult(
                label=detection["label"],
                source=detection["source"],
                box=(
                    min(max(detection["box"][0] / width, 0.0), 1.0),
                    min(max(detection["box"][1] / height, 0.0), 1.0),
                    min(max((detection["box"][2] - detection["box"][0]) / width, 0.0), 1.0),
                    min(max((detection["box"][3] - detection["box"][1]) / height, 0.0), 1.0),
                ),
            )
            for detection in detections
        ]
        postprocess_ms = (time.perf_counter() - postprocess_started) * 1000
        total_ms = (time.perf_counter() - total_started) * 1000
        logger.info(
            "lite_inference od_ms=%.2f dense_ms=%.2f postprocess_ms=%.2f total_ms=%.2f "
            "od_detection_count=%d dense_detection_count=%d final_detection_count=%d",
            od_ms,
            dense_ms,
            postprocess_ms,
            total_ms,
            len(od_detections),
            len(dense_detections),
            len(results),
        )
        return results
