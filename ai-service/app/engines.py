"""Holds the process-wide model singletons, loaded once at app startup."""
from __future__ import annotations

import logging

import torch

from app.config import Settings
from app.pipeline.clip import ClipEngine
from app.pipeline.florence import FlorenceEngine
from app.pipeline.labels import ensure_nltk_data
from app.pipeline.sam import SamEngine

logger = logging.getLogger("ai-service")


class Engines:
    def __init__(self, settings: Settings):
        self.settings = settings
        if settings.device == "auto":
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self.device = settings.device
        self.torch_dtype = torch.float16 if self.device == "cuda" else torch.float32

        self.florence = FlorenceEngine(settings, self.device, self.torch_dtype)
        self.sam = SamEngine(settings, self.device)
        self.clip = ClipEngine(settings, self.device)

    def load_all(self) -> None:
        ensure_nltk_data(str(self.settings.nltk_data_dir))
        self.florence.load()

        # SAM is only needed for per-object cutouts, which the default flow no
        # longer produces — skipping it saves its checkpoint load (2.4GB for
        # vit_h) and the matching RAM.
        logger.info("Skipping SAM: per-object cutouts are not part of the response.")

        if self.settings.needs_clip:
            self.clip.load()
        else:
            logger.info("Skipping CLIP: only used to verify background-vocabulary boxes.")
