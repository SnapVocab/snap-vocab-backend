import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.engines import Engines
from app.routers.detect import router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    engines = Engines(settings)
    app.state.engines = engines
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
    app.include_router(router)
    return app


app = create_app()
