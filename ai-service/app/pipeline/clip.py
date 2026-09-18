"""CLIP-based verification of background-vocabulary detections.

Ported from the `object_scan_florence2` research notebook (cell 10). Only the
"gate the base-vocab boxes" path is kept -- that is what "product mode" (cell 25)
actually uses; the full-detection CLIP re-scoring path was eval-only.
"""
from __future__ import annotations

import logging

import torch
from PIL import Image
from transformers import CLIPModel, CLIPProcessor

from app.config import Settings
from app.pipeline.labels import BASE_VOCAB, EXTRA_HOUSEHOLD_VOCAB

logger = logging.getLogger("ai-service")


class ClipEngine:
    def __init__(self, settings: Settings, device: str):
        self.settings = settings
        self.device = device
        self.model: CLIPModel | None = None
        self.processor: CLIPProcessor | None = None
        # The fixed vocabulary never changes between requests, so its text
        # embeddings are computed once here instead of on every verify() call.
        self._vocab: list[str] = []
        self._vocab_embeds: torch.Tensor | None = None

    def load(self) -> None:
        self.model = CLIPModel.from_pretrained(self.settings.clip_model_id).to(self.device).eval()
        self.processor = CLIPProcessor.from_pretrained(self.settings.clip_model_id)
        self._precompute_vocab_embeds()

    @property
    def loaded(self) -> bool:
        return self.model is not None

    @torch.no_grad()
    def _embed_texts(self, words: list[str]) -> torch.Tensor:
        inputs = self.processor(
            text=[f"a photo of a {w}" for w in words], return_tensors="pt", padding=True,
        ).to(self.device)
        embeds = self.model.get_text_features(**inputs)
        return embeds / embeds.norm(dim=-1, keepdim=True)

    def _precompute_vocab_embeds(self) -> None:
        self._vocab = list(dict.fromkeys(
            list(BASE_VOCAB)
            + (EXTRA_HOUSEHOLD_VOCAB if self.settings.enable_extra_vocab else []),
        ))
        self._vocab_embeds = self._embed_texts(self._vocab)
        logger.info("CLIP: cached text embeddings for %d vocabulary words.", len(self._vocab))

    @torch.no_grad()
    def verify(
        self, raw_image: Image.Image, dets: list[dict], threshold: float, margin: float | None = None,
    ) -> tuple[list[dict], list[tuple]]:
        """Rank-based verification: a crop must match ITS OWN label near the top score, not
        just clear an absolute threshold. This catches "borderline" hallucinations (e.g. a
        wooden spoon boxed as "skis") that would otherwise pass a flat cutoff."""
        if not dets:
            return [], []
        if margin is None:
            margin = self.settings.clip_rank_margin

        crops = []
        for d in dets:
            x1, y1, x2, y2 = (int(v) for v in d["box"])
            crops.append(raw_image.crop((max(0, x1), max(0, y1), max(x1 + 1, x2), max(y1 + 1, y2))))

        # Start from the cached vocabulary; only labels not already in it need
        # a text forward pass this request (usually none).
        candidates = list(self._vocab)
        index_of = {c: i for i, c in enumerate(candidates)}
        extra = [d["label"] for d in dets if d["label"] not in index_of]
        extra = list(dict.fromkeys(extra))

        text_embeds = self._vocab_embeds
        if extra:
            text_embeds = torch.cat([text_embeds, self._embed_texts(extra)], dim=0)
            for word in extra:
                index_of[word] = len(candidates)
                candidates.append(word)

        img_inputs = self.processor(images=crops, return_tensors="pt").to(self.device)
        img_embeds = self.model.get_image_features(**img_inputs)
        img_embeds = img_embeds / img_embeds.norm(dim=-1, keepdim=True)

        sims = img_embeds @ text_embeds.T

        kept, dropped = [], []
        for i, d in enumerate(dets):
            own = index_of[d["label"]]
            s = float(sims[i, own])
            best = float(sims[i].max())
            if s >= threshold and s >= best - margin:
                kept.append(d)
            else:
                best_lbl = candidates[int(sims[i].argmax().item())]
                dropped.append((d["label"], round(s, 3), f"lost to '{best_lbl}' by {round(best - s, 3)}"))
        return kept, dropped
