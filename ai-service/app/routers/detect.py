import asyncio
import hmac
import io
import logging
import time
import uuid

import torch
from fastapi import APIRouter, Depends, Header, Request, UploadFile
from PIL import Image

from app import errors
from app.errors import ServiceError
from app.pipeline.pipeline import encode_base64, process_image
from app.schemas import BoundingBox, Detection, DetectResponse, ErrorResponse, HealthResponse

logger = logging.getLogger("ai-service")

router = APIRouter()

ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}


def require_service_token(
    request: Request, x_service_token: str | None = Header(default=None),
) -> None:
    expected = request.app.state.settings.service_token
    if not expected:
        return
    if not x_service_token or not hmac.compare_digest(x_service_token, expected):
        raise ServiceError(401, errors.UNAUTHORIZED, "Missing or invalid X-Service-Token.")


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    engines = request.app.state.engines
    settings = request.app.state.settings
    return HealthResponse(
        status="ok" if engines and engines.florence.loaded else "loading",
        device=engines.device if engines else "unknown",
        gpu_available=torch.cuda.is_available(),
        model_version=settings.model_version,
        florence_loaded=bool(engines and engines.florence.loaded),
        clip_loaded=bool(engines and engines.clip.loaded),
    )


@router.post(
    "/api/v1/detect",
    response_model=DetectResponse,
    dependencies=[Depends(require_service_token)],
    responses={code: {"model": ErrorResponse} for code in (400, 401, 413, 415, 500, 503)},
)
async def detect(
    request: Request,
    file: UploadFile,
    x_request_id: str | None = Header(default=None),
) -> DetectResponse:
    request_id = x_request_id or uuid.uuid4().hex
    engines = request.app.state.engines
    settings = request.app.state.settings

    if not engines or not engines.florence.loaded:
        raise ServiceError(503, errors.MODEL_NOT_READY, "Models are still loading, try again shortly.")

    if file.content_type not in ALLOWED_TYPES:
        raise ServiceError(415, errors.INVALID_IMAGE, f"Unsupported content type: {file.content_type}")

    raw = await file.read()
    if len(raw) > settings.max_upload_mb * 1024 * 1024:
        raise ServiceError(413, errors.INVALID_IMAGE, f"Image exceeds {settings.max_upload_mb}MB limit.")

    try:
        image = Image.open(io.BytesIO(raw))
        image.load()
    except Exception as exc:
        raise ServiceError(400, errors.INVALID_IMAGE, "Could not decode image.") from exc

    # process_image blocks for tens of seconds. Running it inline would freeze
    # the event loop for that whole time, so /health would hang and no other
    # request could even be accepted. Hand it to a worker thread instead, and
    # let the semaphore keep concurrent inference off the device (two requests
    # at once do not go faster, they just compete for VRAM).
    loop = asyncio.get_running_loop()
    async with request.app.state.inference_slot:
        started = time.perf_counter()
        try:
            result = await loop.run_in_executor(
                None, process_image, settings, engines.florence, engines.clip, image,
            )
        except Exception as exc:
            logger.exception("request_id=%s inference failed", request_id)
            raise ServiceError(500, errors.MODEL_ERROR, "Detection failed inside the model.") from exc
        elapsed_ms = int((time.perf_counter() - started) * 1000)

    detections = [
        Detection(
            label=d["label"],
            headword=d["headword"],
            score=d["score"],
            source=d["source"],
            reliability=d["reliability"],
            box=BoundingBox(x1=d["box"][0], y1=d["box"][1], x2=d["box"][2], y2=d["box"][3]),
        )
        for d in result.detections
    ]
    logger.info(
        "request_id=%s model=%s objects=%d time_ms=%d",
        request_id, settings.model_version, len(detections), elapsed_ms,
    )
    return DetectResponse(
        request_id=request_id,
        model_version=settings.model_version,
        processing_time_ms=elapsed_ms,
        detections=detections,
        labels=[d.label for d in detections],
        annotated_image_base64=encode_base64(result.annotated_image),
        annotated_image_mime=(
            f"image/{settings.annotated_format.lower()}" if result.annotated_image else None
        ),
        image_width=result.width,
        image_height=result.height,
    )
