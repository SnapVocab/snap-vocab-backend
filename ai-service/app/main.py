import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import Settings, get_settings
from app.engines import Engines
from app.routers.detect import router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai-service")


def _configure_timing_log(settings: Settings) -> None:
    """Mirror stage timings to a file so a benchmark run survives the terminal."""
    if settings.timing_log_file is None:
        return
    path = settings.timing_log_file
    path.parent.mkdir(parents=True, exist_ok=True)
    handler = logging.FileHandler(path, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(message)s"))
    timing_logger = logging.getLogger("ai-service.timing")
    timing_logger.addHandler(handler)
    logger.info("Stage timings are also being written to %s", path)


def _warn_on_suspicious_config(settings: Settings) -> None:
    """Flag settings that cost time or memory without buying anything."""
    if settings.max_input_size > 1000 and not settings.enable_tiled_od:
        logger.warning(
            "MAX_INPUT_SIZE=%d but ENABLE_TILED_OD is off. Florence-2 resizes to 768 "
            "internally, so the extra resolution only makes the annotated image heavier. "
            "Either turn tiled OD on or drop MAX_INPUT_SIZE to ~900.",
            settings.max_input_size,
        )
    effective_width = settings.florence_batch_size * settings.florence_num_beams
    if effective_width > 8:
        logger.warning(
            "FLORENCE_BATCH_SIZE=%d x FLORENCE_NUM_BEAMS=%d means %d decode sequences in "
            "flight. Lower one of them if you hit CUDA out of memory.",
            settings.florence_batch_size, settings.florence_num_beams, effective_width,
        )


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    _configure_timing_log(settings)
    _warn_on_suspicious_config(settings)

    engines = Engines(settings)
    app.state.engines = engines
    app.state.inference_slot = asyncio.Semaphore(max(1, settings.max_concurrent_requests))
    logger.info("Loading models on device=%s ...", engines.device)
    # Model loading is synchronous/CPU-and-GPU-bound; run it off the event loop so
    # the process can still answer /health while weights are downloading/loading.
    await asyncio.get_running_loop().run_in_executor(None, engines.load_all)
    logger.info("Models loaded, ready to serve.")
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="SnapVocab AI Service", version="0.1.0", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.state.engines = None
    app.state.inference_slot = asyncio.Semaphore(max(1, settings.max_concurrent_requests))
    app.include_router(router)
    return app


app = create_app()
