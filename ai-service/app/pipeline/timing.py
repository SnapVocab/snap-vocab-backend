"""Per-stage timing so a slow request can be attributed to a concrete stage.

The pipeline runs ~20 model calls for a single image; without this breakdown a
"detection took 90s" report says nothing about which stage to optimise.
"""
from __future__ import annotations

import logging
import time
from contextlib import contextmanager

logger = logging.getLogger("ai-service.timing")


def log_stage(name: str, elapsed: float, extra: str = "") -> None:
    """Log an already-measured duration (for stages spanning several loops)."""
    suffix = f" ({extra})" if extra else ""
    logger.info("%-22s %7.2fs%s", name, elapsed, suffix)


@contextmanager
def stage(name: str, extra: str = ""):
    """Log how long a named pipeline stage took."""
    started = time.perf_counter()
    try:
        yield
    finally:
        log_stage(name, time.perf_counter() - started, extra)
