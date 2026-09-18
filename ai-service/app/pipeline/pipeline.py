"""End-to-end orchestration.

Flow (each step maps to one function below):

    input image
      -> resize
      -> Florence-2 `<OD>`            (one model call by default)
      -> drop boxes that are too small / too large
      -> drop non-dictionary labels
      -> normalise labels, drop duplicates
      -> draw every box onto a single annotated image  (optional)
      -> return labels + boxes + that one image

Extra Florence-2 stages (tiled OD, self-grounding, dense captions, background
vocabulary) stay available behind config flags, but each one adds model calls,
which is where nearly all the latency lives.
"""
from __future__ import annotations

import base64
import logging

from PIL import Image

from app.config import Settings
from app.pipeline.annotate import draw_detections, encode_image, fit_within
from app.pipeline.clip import ClipEngine
from app.pipeline.florence import FlorenceEngine, dedup_detections
from app.pipeline.labels import is_valid_dictionary_word
from app.pipeline.timing import stage

logger = logging.getLogger("ai-service.timing")


class ScanResult:
    __slots__ = ("detections", "annotated_image", "width", "height")

    def __init__(self, detections: list[dict], annotated_image: bytes | None, width: int, height: int):
        self.detections = detections
        self.annotated_image = annotated_image
        # Both the boxes and the annotated image use this one coordinate space,
        # so the client can overlay them without rescaling.
        self.width = width
        self.height = height


def _filter_by_area(detections: list[dict], width: int, height: int, settings: Settings) -> list[dict]:
    """Drop boxes too small to read and boxes that just frame the backdrop."""
    image_area = float(width * height)
    kept = []
    for d in detections:
        x1, y1, x2, y2 = d["box"]
        ratio = max(0.0, (x2 - x1)) * max(0.0, (y2 - y1)) / image_area
        if settings.min_box_area_ratio <= ratio <= settings.max_box_area_ratio:
            kept.append(d)
    return kept


def _unique_by_label(detections: list[dict]) -> list[dict]:
    """One card per word: keep the highest-scoring box for each label."""
    best: dict[str, dict] = {}
    for d in sorted(detections, key=lambda x: -x["score"]):
        best.setdefault(d["label"], d)
    return list(best.values())


def process_image(
    settings: Settings,
    florence: FlorenceEngine,
    clip: ClipEngine,
    raw_image: Image.Image,
) -> ScanResult:
    # 1. Resize — Florence-2 works on 768x768 internally, and this also bounds
    #    the annotated image we send back.
    image = fit_within(raw_image.convert("RGB"), settings.max_input_size)
    logger.info("--- detect start (%dx%d) ---", image.width, image.height)

    # 2. Florence-2 detection
    with stage("1.florence.detect"):
        detections = florence.detect(image)
    n_raw = len(detections)

    # 3. Cheap rejections first: area is pure arithmetic, and the dictionary
    #    check is a cached lookup. Both run before dedup, whose two-tier NMS is
    #    O(n^2) with a WordNet walk per surviving pair — every box dropped here
    #    is a row and a column that NMS never has to consider.
    with stage("2.dedup+filter", f"{n_raw} raw"):
        detections = _filter_by_area(detections, image.width, image.height, settings)
        if settings.require_dictionary_labels:
            detections = [
                d for d in detections
                if is_valid_dictionary_word(d["label"].split()[-1])
            ]
        detections = dedup_detections(detections)
        detections = _unique_by_label(detections)

    # 3b. CLIP only matters when background-vocabulary grounding produced boxes,
    #     which is the one source prone to hallucinating objects.
    base_dets = [d for d in detections if d.get("source") == "base"]
    if base_dets and clip.loaded:
        others = [d for d in detections if d.get("source") != "base"]
        with stage("3.clip.verify", f"{len(base_dets)} base boxes"):
            kept, _dropped = clip.verify(image, base_dets, settings.clip_base_threshold)
        detections = others + kept

    # 4. One annotated image instead of one cropped image per object.
    #    Drawn on `image` (already resized in step 1) and NOT resized again, so
    #    the boxes above stay valid for it. Clients that draw their own overlay
    #    from the boxes can skip this entirely (return_annotated_image=false).
    encoded: bytes | None = None
    if settings.return_annotated_image:
        with stage("4.annotate", f"{len(detections)} boxes"):
            annotated = draw_detections(image, detections)
            encoded = encode_image(annotated, settings.annotated_format, settings.annotated_quality)
        logger.info("annotated image %.1f KB, %d labels", len(encoded) / 1024, len(detections))
    else:
        logger.info("annotated image skipped, %d labels", len(detections))

    return ScanResult(detections, encoded, image.width, image.height)


def encode_base64(data: bytes | None) -> str | None:
    return None if data is None else base64.b64encode(data).decode("ascii")
