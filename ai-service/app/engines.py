"""Holds the process-wide model singletons, loaded once at app startup."""
from __future__ import annotations

import torch

from app.config import Settings
from app.pipeline.clip import ClipEngine
from app.pipeline.florence import FlorenceEngine
from app.pipeline.labels import ensure_nltk_data
from app.pipeline.sam import SamEngine


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
        self.sam.load()
        self.clip.load()
