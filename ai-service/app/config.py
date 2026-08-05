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
    florence_model_id: str = "microsoft/Florence-2-base"
    sam_model_type: str = "vit_b"  # vit_h | vit_l | vit_b
    sam_checkpoint_dir: Path = BASE_DIR / "checkpoints"
    clip_model_id: str = "openai/clip-vit-base-patch32"
    nltk_data_dir: Path = BASE_DIR / "nltk_data"

    florence_max_new_tokens: int = 1024
    florence_num_beams: int = 3

    # ---- input handling ----
    # Everything downstream (detection boxes AND the annotated image sent back)
    # lives in this one coordinate space, so the client never has to rescale.
    # Measured: raising this from 900 to 1400 costs no extra time (Florence-2
    # resizes to 768 internally anyway) but recovers small objects once tiled OD
    # is on, because each tile then carries more detail (faucet, drawer).
    max_input_size: int = 1400

    # ---- detection stages ----
    # Measured on hell.webp (CPU, Florence-2-base), labels / ground-truth hits:
    #   <OD> alone .................. 12s,  4 labels,  4/33
    #   + self-grounding ............ 34s, 16 labels, 13/33   <- default
    #   + tiled OD .................. 64s, 17 labels, 14/33
    #   + base vocabulary ........... 83s, 30 labels, 18/33 (but ~40% false positives)
    # Self-grounding is by far the best value; tiled OD and dense captions each
    # cost 20-30s for roughly one extra label.
    enable_tiled_od: bool = False        # +4 calls, catches small objects
    enable_self_grounding: bool = True   # +2 calls, labels from a generated caption
    enable_dense_caption: bool = False   # +1 call, dense region captions
    enable_base_vocab: bool = False      # +7 calls, grounds a background vocabulary
    enable_extra_vocab: bool = False     # +7 more calls, and measured WORSE than base alone

    strict_dictionary_filter: bool = False
    require_dictionary_labels: bool = True

    # ---- box filtering ----
    # Boxes smaller than this fraction of the image are usually noise/texture and
    # make poor flashcards; boxes larger than the max are backdrop ("countertop",
    # "wall") rather than an object worth learning.
    min_box_area_ratio: float = 0.004
    max_box_area_ratio: float = 0.85

    # ---- annotated image returned to the client ----
    annotated_format: str = "WEBP"  # WEBP is ~15x smaller than PNG here
    annotated_quality: int = 85

    # ---- CLIP verification (only used when base-vocab grounding is enabled) ----
    clip_base_threshold: float = 0.23
    clip_rank_margin: float = 0.02

    @property
    def needs_clip(self) -> bool:
        return self.enable_base_vocab


@lru_cache
def get_settings() -> Settings:
    return Settings()
