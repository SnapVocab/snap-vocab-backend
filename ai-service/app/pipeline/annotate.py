"""Draw every detection onto a single annotated image.

Replaces the previous per-object SAM cutouts: one annotated image is ~18x
smaller than 18 full-resolution PNG crops, and it is what the app actually
shows the user.
"""
from __future__ import annotations

import io

from PIL import Image, ImageDraw, ImageFont

# Distinct, high-contrast colours cycled per box.
BOX_COLORS = [
    (139, 92, 246),   # violet
    (34, 197, 94),    # green
    (245, 158, 11),   # amber
    (236, 72, 153),   # pink
    (6, 182, 212),    # cyan
    (239, 68, 68),    # red
]


def _font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        # Pillow < 10.1 has no size argument on the default font.
        return ImageFont.load_default()


def draw_detections(image: Image.Image, detections: list[dict]) -> Image.Image:
    """Return a copy of `image` with a labelled box drawn for each detection."""
    canvas = image.convert("RGB").copy()
    draw = ImageDraw.Draw(canvas)

    # Scale line/text with image size so annotations stay readable on any input.
    scale = max(canvas.width, canvas.height) / 900
    line_width = max(2, round(3 * scale))
    font = _font(max(12, round(16 * scale)))

    for index, det in enumerate(detections):
        color = BOX_COLORS[index % len(BOX_COLORS)]
        x1, y1, x2, y2 = det["box"]
        draw.rectangle([x1, y1, x2, y2], outline=color, width=line_width)

        caption = f'{det["label"]} {round(det["score"] * 100)}%'
        text_box = draw.textbbox((0, 0), caption, font=font)
        text_w, text_h = text_box[2] - text_box[0], text_box[3] - text_box[1]
        pad = max(2, round(4 * scale))

        # Put the label above the box, or inside it when there is no room above.
        label_y = y1 - text_h - 2 * pad
        if label_y < 0:
            label_y = y1
        label_x = min(x1, canvas.width - text_w - 2 * pad)
        label_x = max(0, label_x)

        draw.rectangle(
            [label_x, label_y, label_x + text_w + 2 * pad, label_y + text_h + 2 * pad],
            fill=color,
        )
        draw.text((label_x + pad, label_y + pad), caption, fill=(255, 255, 255), font=font)

    return canvas


def encode_image(image: Image.Image, fmt: str, quality: int) -> bytes:
    buf = io.BytesIO()
    if fmt.upper() == "WEBP":
        image.save(buf, format="WEBP", quality=quality)
    else:
        image.save(buf, format=fmt.upper(), optimize=True)
    return buf.getvalue()


def fit_within(image: Image.Image, max_size: int) -> Image.Image:
    """Downscale so the longest side is at most `max_size`. Never upscales."""
    if max(image.width, image.height) <= max_size:
        return image
    resized = image.copy()
    resized.thumbnail((max_size, max_size), Image.LANCZOS)
    return resized
