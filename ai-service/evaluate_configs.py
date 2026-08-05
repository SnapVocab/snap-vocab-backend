"""So sánh độ phủ / thời gian giữa các cấu hình pipeline trên cùng một ảnh.

Nạp Florence-2 một lần rồi chạy lần lượt từng cấu hình, nên nhanh hơn nhiều so
với việc khởi động lại service cho mỗi lần đo.

    .venv\\Scripts\\python.exe evaluate_configs.py ..\\hell.webp
"""
from __future__ import annotations

import io
import logging
import sys
import time

from PIL import Image

from app.config import get_settings
from app.engines import Engines
from app.pipeline.pipeline import process_image

# Những vật thể có thể nhìn thấy rõ trong hell.webp, dùng làm mốc đối chiếu.
GROUND_TRUTH = [
    "person", "human face", "hand", "baby",
    "turkey", "chicken", "bell pepper", "carrot", "kale", "vegetable",
    "pot", "bowl", "bottle", "spatula", "mug", "jar",
    "microwave", "oven", "stove", "refrigerator", "cabinet", "drawer",
    "countertop", "sink", "faucet", "window", "tablet", "scale",
    "apron", "tank top", "shirt", "bag", "toy",
]

SELF = {"enable_self_grounding": True}

# (ten, overrides, che_do_anh)
#   che_do_anh: "goc" giu nguyen anh, "jpeg80" nen lai giong ImagePicker cua app
CONFIGS: list[tuple[str, dict, str]] = [
    ("A. self @900 (mac dinh)", SELF, "goc"),
    ("B. self @900, anh nen JPEG80", SELF, "jpeg80"),
    ("C. self @1400", {**SELF, "max_input_size": 1400}, "goc"),
    ("D. self + tiled @900", {**SELF, "enable_tiled_od": True}, "goc"),
    ("E. self + tiled @1400", {**SELF, "enable_tiled_od": True, "max_input_size": 1400}, "goc"),
    ("F. self + tiled + base @1400", {
        **SELF, "enable_tiled_od": True, "enable_base_vocab": True, "max_input_size": 1400,
    }, "goc"),
]

BASE_FLAGS = {
    "enable_tiled_od": False,
    "enable_self_grounding": False,
    "enable_dense_caption": False,
    "enable_base_vocab": False,
    "enable_extra_vocab": False,
    "min_box_area_ratio": 0.004,
    "max_input_size": 900,
}


def as_app_would_send(image: Image.Image) -> Image.Image:
    """Bắt chước ImagePicker cua app: nen lai JPEG chat luong 80%."""
    buf = io.BytesIO()
    image.convert("RGB").save(buf, format="JPEG", quality=80)
    buf.seek(0)
    return Image.open(buf)


def main(image_path: str) -> None:
    logging.disable(logging.INFO)

    settings = get_settings()
    engines = Engines(settings)
    print("Dang nap Florence-2...", flush=True)
    engines.load_all()
    if any(o.get("enable_base_vocab") for _n, o, _m in CONFIGS) and not engines.clip.loaded:
        print("Dang nap CLIP...", flush=True)
        engines.clip.load()
    print(f"Da nap tren {engines.device}.\n", flush=True)

    original = Image.open(image_path)
    results = []

    for name, overrides, image_mode in CONFIGS:
        for key, value in {**BASE_FLAGS, **overrides}.items():
            setattr(settings, key, value)

        image = as_app_would_send(original) if image_mode == "jpeg80" else original

        started = time.perf_counter()
        result = process_image(settings, engines.florence, engines.clip, image)
        elapsed = time.perf_counter() - started

        labels = sorted({d["label"] for d in result.detections})
        results.append((name, elapsed, labels))
        print(f"{name:<32} {elapsed:6.1f}s  {len(labels):2d} nhan  {', '.join(labels)}", flush=True)

    truth = {t.lower() for t in GROUND_TRUTH}
    print("\n" + "=" * 74)
    print(f"{'Cau hinh':<32} {'Thoi gian':>10} {'So nhan':>8} {'Dung/33':>10}")
    print("=" * 74)
    for name, elapsed, labels in results:
        hit = {l for l in labels if l.lower() in truth}
        print(f"{name:<32} {elapsed:9.1f}s {len(labels):8d} {len(hit):9d}")

    all_found = {l.lower() for _n, _e, labels in results for l in labels}
    print("\nTu trong ground-truth khong cau hinh nao bat duoc:")
    missing = sorted(truth - all_found)
    print("  " + (", ".join(missing) if missing else "(khong co)"))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "../hell.webp")
