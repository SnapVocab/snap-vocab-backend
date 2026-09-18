from typing import Literal

from pydantic import BaseModel, Field

Source = Literal["od", "od_tile", "self", "dense", "base"]
Reliability = Literal["HIGH", "MEDIUM", "LOW"]


class BoundingBox(BaseModel):
    x1: float
    y1: float
    x2: float
    y2: float


class Detection(BaseModel):
    label: str = Field(description="English word or phrase for the detected object")
    headword: str = Field(
        description="Last word of `label` in singular noun form (\"coffee mugs\" -> \"mug\"); "
                    "the dictionary fallback when `label` itself has no entry",
    )
    score: float = Field(description="Fixed per-source constant used for ranking, NOT a measured confidence")
    source: Source = Field(description="Which stage produced this detection")
    reliability: Reliability = Field(description="Coarse trust level derived from `source`")
    box: BoundingBox = Field(
        description="Pixel coordinates in the image_width x image_height space "
                    "(EXIF rotation already applied, then resized)",
    )


class DetectResponse(BaseModel):
    request_id: str | None = Field(default=None, description="Echo of the X-Request-Id header")
    model_version: str = Field(default="", description="Florence-2 model and enabled stages")
    processing_time_ms: int = Field(default=0, description="Inference time, excluding time queued")
    detections: list[Detection]
    labels: list[str]
    annotated_image_base64: str | None = Field(
        default=None,
        description="Base64 image (no data: prefix) of the input with every box drawn on it; "
                    "null when RETURN_ANNOTATED_IMAGE is off and the client draws its own overlay",
    )
    annotated_image_mime: str | None = Field(
        default=None, description="MIME type of annotated_image_base64, null when it is absent",
    )
    image_width: int = Field(description="Width the boxes and annotated image share")
    image_height: int = Field(description="Height the boxes and annotated image share")


class ErrorDetail(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorDetail


class HealthResponse(BaseModel):
    status: str
    device: str
    gpu_available: bool
    model_version: str
    florence_loaded: bool
    clip_loaded: bool
