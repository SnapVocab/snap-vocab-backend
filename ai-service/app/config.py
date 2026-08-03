from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="", extra="ignore")

    # ---- runtime ----
    device: str = "cuda"  # "cuda" | "cpu" | "auto"
    cors_origins: list[str] = ["*"]
    max_upload_mb: int = 15

    # ---- models ----
    florence_model_id: str = "microsoft/Florence-2-large"
    sam_model_type: str = "vit_h"  # vit_h | vit_l | vit_b
    sam_checkpoint_dir: Path = BASE_DIR / "checkpoints"
    clip_model_id: str = "openai/clip-vit-base-patch32"
    nltk_data_dir: Path = BASE_DIR / "nltk_data"

    florence_max_new_tokens: int = 1024
    florence_num_beams: int = 3

    # ---- detection pipeline toggles (defaults = notebook "product mode") ----
    enable_tiled_od: bool = True
    enable_self_grounding: bool = True
    enable_base_vocab: bool = True
    enable_extra_vocab: bool = True
    max_cards_per_label: int = 1
    strict_dictionary_filter: bool = False
    require_dictionary_labels: bool = True

    # ---- CLIP verification (gates the base-vocab detections) ----
    clip_base_threshold: float = 0.23
    clip_rank_margin: float = 0.02

    # ---- SAM segmentation ----
    sam_iou_weight: float = 0.7
    sam_stability_weight: float = 0.3
    sam_min_mask_area_px: int = 400
    sam_min_mask_box_ratio: float = 0.05
    sam_crop_padding: int = 4


@lru_cache
def get_settings() -> Settings:
    return Settings()
