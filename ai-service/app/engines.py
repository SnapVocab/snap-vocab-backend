"""Holds the process-wide model singletons, loaded once at app startup."""
from __future__ import annotations

import logging

import torch

from app.config import Settings
from app.pipeline.clip import ClipEngine
from app.pipeline.florence import FlorenceEngine
from app.pipeline.labels import ensure_nltk_data

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
        self.clip = ClipEngine(settings, self.device)

    def load_all(self) -> None:
        if self.device == "cuda":
            # Matmuls in the vision tower run in fp16 anyway; letting the
            # remaining fp32 ops use TF32 costs no accuracy that matters here.
            torch.backends.cuda.matmul.allow_tf32 = True
            torch.backends.cudnn.allow_tf32 = True

        ensure_nltk_data(str(self.settings.nltk_data_dir))
        self.florence.load()

        if self.settings.needs_clip:
            self.clip.load()
        else:
            logger.info("Skipping CLIP: only used to verify background-vocabulary boxes.")

        if self.settings.warmup_on_load:
            self.florence.warmup()
