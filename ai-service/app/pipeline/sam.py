"""SAM (Segment Anything) model loading and box-to-mask segmentation.

Ported from the `object_scan_florence2` research notebook (cells 4 + 10).
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np
import torch
from segment_anything import SamPredictor, sam_model_registry

from app.config import Settings

SAM_CHECKPOINT_URLS = {
    "vit_b": "https://dl.fbaipublicfiles.com/segment_anything/sam_vit_b_01ec64.pth",
    "vit_l": "https://dl.fbaipublicfiles.com/segment_anything/sam_vit_l_0b3195.pth",
    "vit_h": "https://dl.fbaipublicfiles.com/segment_anything/sam_vit_h_4b8939.pth",
}


def _ensure_checkpoint(model_type: str, checkpoint_dir: Path) -> Path:
    import urllib.request

    checkpoint_dir.mkdir(parents=True, exist_ok=True)
    url = SAM_CHECKPOINT_URLS[model_type]
    ckpt_path = checkpoint_dir / url.split("/")[-1]
    if not ckpt_path.exists():
        urllib.request.urlretrieve(url, ckpt_path)  # noqa: S310 -- fixed, trusted Meta URL
    return ckpt_path


def _stability_score(mask_logits: np.ndarray, threshold: float = 0.0, offset: float = 1.0) -> float:
    inter = np.count_nonzero(mask_logits > (threshold + offset))
    union = np.count_nonzero(mask_logits > (threshold - offset))
    return float(inter) / float(union) if union > 0 else 0.0


def _clean_mask(binary_mask: np.ndarray) -> np.ndarray:
    mask_u8 = binary_mask.astype(np.uint8) * 255
    kernel = np.ones((3, 3), np.uint8)
    mask_u8 = cv2.morphologyEx(mask_u8, cv2.MORPH_CLOSE, kernel, iterations=1)
    mask_u8 = cv2.morphologyEx(mask_u8, cv2.MORPH_OPEN, kernel, iterations=1)
    n_labels, labels, stats, _ = cv2.connectedComponentsWithStats(mask_u8, connectivity=8)
    if n_labels <= 1:
        return binary_mask
    largest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    return labels == largest


class SamEngine:
    def __init__(self, settings: Settings, device: str):
        self.settings = settings
        self.device = device
        self.predictor: SamPredictor | None = None

    def load(self) -> None:
        ckpt = _ensure_checkpoint(self.settings.sam_model_type, self.settings.sam_checkpoint_dir)
        sam = sam_model_registry[self.settings.sam_model_type](checkpoint=str(ckpt)).to(self.device)
        self.predictor = SamPredictor(sam)

    @property
    def loaded(self) -> bool:
        return self.predictor is not None

    def set_image(self, image_np: np.ndarray) -> None:
        self.predictor.set_image(image_np)

    @torch.no_grad()
    def segment_box(self, box: list[float]) -> tuple[np.ndarray, float, float]:
        box_np = np.array(box)
        x1, y1, x2, y2 = box_np
        point_coords = np.array([[(x1 + x2) / 2.0, (y1 + y2) / 2.0]])
        point_labels = np.array([1])
        raw_masks, iou_preds, _ = self.predictor.predict(
            box=box_np, point_coords=point_coords, point_labels=point_labels,
            multimask_output=True, return_logits=True,
        )
        stability = np.array([_stability_score(m) for m in raw_masks])
        best = int(np.argmax(
            self.settings.sam_iou_weight * iou_preds + self.settings.sam_stability_weight * stability,
        ))
        mask = raw_masks[best] > self.predictor.model.mask_threshold
        return _clean_mask(mask), float(iou_preds[best]), float(stability[best])
