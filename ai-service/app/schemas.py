from pydantic import BaseModel, Field


class BoundingBox(BaseModel):
    x1: float
    y1: float
    x2: float
    y2: float


class Detection(BaseModel):
    label: str = Field(description="English word for the detected object")
    score: float = Field(description="Pseudo-confidence score in [0, 1]")
    source: str = Field(description="Which stage produced this detection: od | od_tile | self | dense | base")
    box: BoundingBox
    image_base64: str | None = Field(
        default=None,
        description="Base64-encoded PNG (RGBA, transparent background) of the SAM-segmented crop",
    )


class DetectResponse(BaseModel):
    detections: list[Detection]
    labels: list[str]


class HealthResponse(BaseModel):
    status: str
    device: str
    florence_loaded: bool
    sam_loaded: bool
    clip_loaded: bool
