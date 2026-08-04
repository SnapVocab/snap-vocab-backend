import io

from fastapi import APIRouter, HTTPException, Request, UploadFile
from PIL import Image

from app.pipeline.pipeline import encode_base64, process_image
from app.schemas import BoundingBox, Detection, DetectResponse, HealthResponse

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    engines = request.app.state.engines
    return HealthResponse(
        status="ok" if engines and engines.florence.loaded else "loading",
        device=engines.device if engines else "unknown",
        florence_loaded=bool(engines and engines.florence.loaded),
        sam_loaded=bool(engines and engines.sam.loaded),
        clip_loaded=bool(engines and engines.clip.loaded),
    )


@router.post("/api/v1/detect", response_model=DetectResponse)
async def detect(request: Request, file: UploadFile) -> DetectResponse:
    engines = request.app.state.engines
    settings = engines.settings

    if not engines.florence.loaded:
        raise HTTPException(status_code=503, detail="Models are still loading, try again shortly.")

    if file.content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=415, detail=f"Unsupported content type: {file.content_type}")

    raw = await file.read()
    if len(raw) > settings.max_upload_mb * 1024 * 1024:
        raise HTTPException(status_code=413, detail=f"Image exceeds {settings.max_upload_mb}MB limit.")

    try:
        image = Image.open(io.BytesIO(raw))
        image.load()
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Could not decode image.") from exc

    result = process_image(settings, engines.florence, engines.clip, image)

    detections = [
        Detection(
            label=d["label"],
            score=d["score"],
            source=d["source"],
            box=BoundingBox(x1=d["box"][0], y1=d["box"][1], x2=d["box"][2], y2=d["box"][3]),
        )
        for d in result.detections
    ]
    return DetectResponse(
        detections=detections,
        labels=[d.label for d in detections],
        annotated_image_base64=encode_base64(result.annotated_image),
        annotated_image_mime=f"image/{settings.annotated_format.lower()}",
        image_width=result.width,
        image_height=result.height,
    )
