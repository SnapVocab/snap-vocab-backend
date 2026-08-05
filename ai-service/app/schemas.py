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
    box: BoundingBox = Field(
        description="Pixel coordinates in the annotated image's coordinate space",
    )


class DetectResponse(BaseModel):
    detections: list[Detection]
    labels: list[str]
    annotated_image_base64: str = Field(
        description="Base64 image (no data: prefix) of the input with every box drawn on it",
    )
    annotated_image_mime: str = Field(description="MIME type of annotated_image_base64")
    image_width: int = Field(description="Width the boxes and annotated image share")
    image_height: int = Field(description="Height the boxes and annotated image share")


class HealthResponse(BaseModel):
    status: str
    device: str
    florence_loaded: bool
    sam_loaded: bool
    clip_loaded: bool
