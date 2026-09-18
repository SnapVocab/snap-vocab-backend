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

    # Only one image is processed at a time: Florence-2 generation saturates the
    # device, and two concurrent requests on a 4GB card OOM instead of going
    # faster. Requests beyond this queue up rather than being rejected.
    max_concurrent_requests: int = 1

    # ---- models ----
    florence_model_id: str = "microsoft/Florence-2-base"
    clip_model_id: str = "openai/clip-vit-base-patch32"
    nltk_data_dir: Path = BASE_DIR / "nltk_data"

    florence_max_new_tokens: int = 1024
    # Beam search multiplies decoder work by this factor, so dropping it to 1 is
    # tempting. Measured on hell.webp (CPU, Florence-2-base, self-grounding on):
    #   num_beams=3 .... 45.4s, 16 labels, 13/33 ground-truth hits
    #   num_beams=1 .... 34.6s, 11 labels,  7/33 ground-truth hits
    # That is -24% time for -46% correct labels, and it invents objects that are
    # not there ("dog", "leash" in a kitchen photo). An older note in this repo
    # claimed the cost was ~3%; it is not. Keep 3.
    florence_num_beams: int = 3

    # How many prompts to feed Florence-2 in a single generate() call. Only
    # prompts of identical token length can share a call (Florence-2 does not
    # mask padding -- see FlorenceEngine._generate), so this helps the tiled OD
    # stage fully and the background-vocabulary stage partially. Effective decode
    # width is florence_batch_size * florence_num_beams; lower this first on OOM.
    florence_batch_size: int = 4

    # ---- input handling ----
    # Everything downstream (detection boxes AND the annotated image sent back)
    # lives in this one coordinate space, so the client never has to rescale.
    # NOTE: Florence-2 resizes to 768x768 internally, so raising this only helps
    # when enable_tiled_od is on (each tile then carries more detail). With tiled
    # OD off it just makes the annotated image heavier -- see _warn_on_suspicious_config.
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
    # The response already carries every box in pixel space, so a client that has
    # the original image can draw the overlay itself. Turning this off removes a
    # WEBP encode plus a base64 payload that inflates the response by ~33%.
    # Kept on by default because the mobile app currently renders this image.
    return_annotated_image: bool = True
    annotated_format: str = "WEBP"  # WEBP is ~15x smaller than PNG here
    annotated_quality: int = 85

    # ---- CLIP verification (only used when base-vocab grounding is enabled) ----
    clip_base_threshold: float = 0.23
    clip_rank_margin: float = 0.02

    # ---- observability ----
    # Stage timings go to stdout by default, which is lost when the terminal
    # closes. Point this at a file to keep a baseline worth comparing against.
    timing_log_file: Path | None = None

    # Run one tiny image through Florence-2 at startup. The first CUDA call pays
    # for kernel init and cuDNN autotuning; without this the first real request
    # absorbs that cost.
    warmup_on_load: bool = True

    @property
    def needs_clip(self) -> bool:
        return self.enable_base_vocab


@lru_cache
def get_settings() -> Settings:
    return Settings()
