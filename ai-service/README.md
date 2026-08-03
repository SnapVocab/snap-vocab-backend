# SnapVocab AI Service

Service FastAPI nhận diện đồ vật trong ảnh và trả về tên tiếng Anh của chúng,
để backend tra nghĩa và app dựng thẻ từ vựng.

Được port từ notebook nghiên cứu `object_scan_florence2`, giữ nguyên pipeline:
**Florence-2** (nhận diện vật thể từ vựng mở) → **CLIP** (xác thực các nhãn từ
vựng nền có thật trong ảnh, chống ảo giác) → **SAM** (phân đoạn và cắt từng vật
thể ra nền trong suốt).

## Vị trí trong hệ thống

```
Mobile App (React Native)
      ↓  POST /api/scan  (multipart, kèm JWT)
Backend (Spring Boot, cổng 8080)
      ↓  POST /api/v1/detect  (multipart)
AI Service (FastAPI, cổng 8000)   ←── tài liệu này
      ↑  trả nhãn + box + ảnh cắt nền
Backend tra nghĩa trong MySQL rồi gộp lại trả về App
```

App **không** gọi thẳng AI Service. Backend đứng giữa, cấu hình đường dẫn tới
service này nằm ở `ai-service.base-url` trong `application-dev.yaml`
(mặc định `http://localhost:8000`).

## Yêu cầu

- Python 3.10+ (đã kiểm chứng trên 3.10)
- **Chạy CPU**: cần tối thiểu ~4GB RAM trống với model nhẹ, ~10GB với model đầy đủ
- **Chạy GPU**: NVIDIA ≥ 12GB VRAM cho cấu hình đầy đủ (notebook gốc chạy trên
  Colab T4 16GB)

## Cài đặt

```powershell
cd ai-service
py -3.10 -m venv .venv
.venv\Scripts\pip.exe install --upgrade pip

# Bản CPU (nhẹ hơn, dùng khi máy không có GPU NVIDIA)
.venv\Scripts\pip.exe install torch==2.5.1 torchvision==0.20.1 --index-url https://download.pytorch.org/whl/cpu

# Bản CUDA (chỉ khi có GPU NVIDIA) — dùng THAY CHO dòng trên
# .venv\Scripts\pip.exe install torch==2.5.1 torchvision==0.20.1 --index-url https://download.pytorch.org/whl/cu121

.venv\Scripts\pip.exe install -r requirements.txt
copy .env.example .env
```

> **Lưu ý về torch**: `requirements.txt` không ghim bản CPU hay CUDA, nên phải
> cài `torch` trước bằng đúng index-url mong muốn. Kiểm tra bằng:
>
> ```powershell
> .venv\Scripts\python.exe -c "import torch; print(torch.__version__, torch.cuda.is_available())"
> ```
>
> Ra `2.5.1+cpu False` nghĩa là bản CPU — lúc này **bắt buộc** để `DEVICE=cpu`
> trong `.env`, nếu để `DEVICE=cuda` service sẽ crash ngay khi khởi động.

Lần chạy đầu sẽ tải model (Florence-2, checkpoint SAM vào `checkpoints/`, CLIP,
và bộ dữ liệu NLTK vào `nltk_data/`) — vài GB, mất vài phút trước khi `/health`
báo `"status": "ok"`.

## Chạy

```powershell
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Model được nạp **một lần lúc khởi động** (không nạp lại mỗi request). Trong lúc
đang nạp, `/health` vẫn trả lời nhưng với `"status": "loading"`, và
`/api/v1/detect` trả `503`.

Hoặc chạy bằng Docker (máy có GPU, cần NVIDIA Container Toolkit):

```bash
docker build -t snapvocab-ai-service .
docker run --gpus all -p 8000:8000 snapvocab-ai-service
```

## API

### `GET /health`

Cho biết từng model đã nạp xong chưa.

```json
{
  "status": "ok",
  "device": "cpu",
  "florence_loaded": true,
  "sam_loaded": true,
  "clip_loaded": true
}
```

### `POST /api/v1/detect`

`multipart/form-data` với field `file` (`image/jpeg`, `image/png` hoặc
`image/webp`, mặc định tối đa 15MB).

```bash
curl -X POST http://localhost:8000/api/v1/detect -F "file=@photo.jpg"
```

Kết quả:

```json
{
  "labels": ["mug", "laptop", "chair"],
  "detections": [
    {
      "label": "mug",
      "score": 0.9,
      "source": "od",
      "box": { "x1": 120.0, "y1": 80.0, "x2": 340.0, "y2": 310.0 },
      "image_base64": "iVBORw0KGgoAAAANSUhEUgAA..."
    }
  ]
}
```

- `box` là toạ độ pixel theo **kích thước gốc của ảnh gửi lên** — app dùng
  toạ độ này để vẽ khung nhận diện đè lên ảnh.
- `image_base64` là ảnh PNG nền trong suốt (do SAM cắt), dùng làm ảnh thẻ
  từ vựng. Chuỗi này **không** có tiền tố `data:`.
- `source` cho biết nhãn đến từ bước nào trong pipeline:

| `source`  | Nguồn |
|-----------|-------|
| `od`      | `<OD>` — nhận diện trên toàn ảnh |
| `od_tile` | `<OD>` trên 4 ô chồng lấn — chuyên bắt vật nhỏ |
| `self`    | Model tự mô tả ảnh rồi ground chính caption đó |
| `dense`   | `<DENSE_REGION_CAPTION>` |
| `base`    | Ground bộ từ vựng nền, đã qua CLIP xác thực |

## Cấu hình

Toàn bộ tham số nằm trong [`app/config.py`](app/config.py), ghi đè được qua
`.env` hoặc biến môi trường. **Giá trị mặc định trong `config.py` chính là
"chế độ sản phẩm" của notebook (cell 25)** — nghĩa là nếu `.env` không ghi đè
gì thì service chạy y hệt notebook.

### Các cờ quan trọng

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DEVICE` | `cuda` | `cuda` \| `cpu` \| `auto` |
| `FLORENCE_MODEL_ID` | `microsoft/Florence-2-large` | Đổi sang `Florence-2-base` để nhẹ hơn |
| `SAM_MODEL_TYPE` | `vit_h` | `vit_h` \| `vit_l` \| `vit_b` (`vit_b` nhẹ nhất) |
| `ENABLE_TILED_OD` | `true` | Quét thêm 4 ô để bắt vật nhỏ (+4 lượt gọi model) |
| `ENABLE_SELF_GROUNDING` | `true` | Tự mô tả ảnh rồi ground caption (+2 lượt) |
| `ENABLE_BASE_VOCAB` | `true` | Quét 80 từ vựng nền (**+7 lượt**, tốn nhất) |
| `ENABLE_EXTRA_VOCAB` | `true` | Thêm 79 từ gia dụng vào bộ nền (**+7 lượt nữa**) |
| `MAX_CARDS_PER_LABEL` | `1` | Mỗi từ chỉ giữ 1 thẻ điểm cao nhất |
| `CLIP_BASE_THRESHOLD` | `0.23` | Sàn điểm CLIP cho nhãn nguồn `base` |
| `CLIP_RANK_MARGIN` | `0.02` | Biên độ thứ hạng CLIP (chống ảo giác "na ná") |
| `MAX_UPLOAD_MB` | `15` | Giới hạn dung lượng ảnh |

### Ba bộ cấu hình sẵn có

| File | Dùng khi |
|---|---|
| `.env.example` | Mẫu gốc để tạo `.env` |
| `.env` | Cấu hình đang chạy (đã tinh chỉnh cho máy CPU yếu) |
| `.env.notebook` | Y hệt notebook — dùng khi có GPU đủ mạnh |

Bật cấu hình notebook:

```powershell
copy .env.notebook .env
# Nếu vẫn chạy CPU: mở .env sửa DEVICE=cuda thành DEVICE=cpu
```

## Đánh đổi giữa chất lượng và tốc độ

Số lượt gọi Florence-2 cho mỗi ảnh quyết định thời gian xử lý:

| Cấu hình | Số lượt gọi | Số nhãn (ảnh mẫu) | Thời gian (CPU i5-10300H) |
|---|---|---|---|
| Chỉ `OD` + `dense` | 2 | 4 | ~26 giây |
| Thêm `TILED_OD` + `SELF_GROUNDING` | ~8 | 18 | ~83 giây |
| Đầy đủ như notebook (thêm `BASE_VOCAB` + `EXTRA_VOCAB`) | ~22 | nhiều nhất | nhiều phút |

Đây là số đo thật với `Florence-2-base` + `SAM vit_b`. Dùng `Florence-2-large`
sẽ chậm hơn vài lần nữa nhưng nhãn chính xác và cụ thể hơn (ví dụ nhận đúng
`turkey` thay vì `chicken`).

**Cảnh báo timeout**: app đặt timeout 180 giây
(`src/features/scan/api.ts`), backend đặt `read-timeout-ms: 600000`
(`application-dev.yaml`). Nếu AI xử lý lâu hơn, app sẽ báo lỗi **dù AI vẫn
đang chạy bình thường**. Muốn dùng cấu hình đầy đủ trên CPU thì phải nâng cả
hai timeout này lên.

## Sự cố thường gặp

**Service crash ngay khi khởi động, không có traceback (exit code 139 / segfault)**
→ Hết RAM. `Florence-2-large` + `SAM vit_h` trên CPU/fp32 cần khoảng 8-9GB.
Kiểm tra RAM trống, đóng bớt ứng dụng, hoặc đổi sang `Florence-2-base` +
`vit_b`.

**Lỗi liên quan CUDA khi để `DEVICE=cuda`** (ví dụ `Torch not compiled with
CUDA enabled`, `AssertionError: Torch not compiled...`)
→ Torch đang là bản CPU-only. Kiểm tra bằng
`.venv\Scripts\python.exe -c "import torch; print(torch.cuda.is_available())"`.
Xem mục [Cài đặt](#cài-đặt) để cài lại bản CUDA, hoặc để `DEVICE=cpu`.

**`LookupError: Resource wordnet not found`**
→ Dữ liệu NLTK chưa tải xong. Bình thường `ensure_nltk_data()` tự tải lúc
khởi động; nếu mạng chặn, tải thủ công vào thư mục `nltk_data/`.

**`/api/v1/detect` trả 503**
→ Model chưa nạp xong. Đợi `/health` báo `"status": "ok"`.

**Backend báo timeout nhưng AI service vẫn đang chạy**
→ Xem mục cảnh báo timeout ở trên.

## Ghi chú về việc port từ notebook

- Chỉ giữ nhánh "chế độ sản phẩm". Bộ đánh giá COCO/Open-Images, các cờ
  ablation, và phần upload riêng của Colab đã được bỏ.
- Các cờ notebook đặt `False` ở chế độ sản phẩm (`ENABLE_TARGETED_GROUNDING`,
  `ENABLE_CLIP_VERIFY_ALL`, `ENABLE_BASE_OVERLAP_VETO`, `ENABLE_CLIP_SCORING`)
  không được port sang — tương đương trạng thái tắt.
- Model nạp một lần lúc khởi động (`app/engines.py`), không nạp lại mỗi request.
- Ảnh cắt được trả về dạng base64 trong bộ nhớ, không ghi ra đĩa như notebook.
- `is_concrete_object_word` trả `None` (WordNet không có nghĩa danh từ nào)
  được coi là **hợp lệ**, giống hành vi lọc mềm của notebook — chỉ khi trả về
  `False` tường minh thì nhãn mới bị loại.
- Dữ liệu WordNet được nạp lười (lazy) vì `ensure_nltk_data()` chỉ chạy lúc
  khởi động app, sau thời điểm module `labels.py` được import.

## Cấu trúc thư mục

```
ai-service/
├── app/
│   ├── main.py              # FastAPI app, nạp model lúc startup
│   ├── engines.py           # Giữ singleton Florence-2 / SAM / CLIP
│   ├── config.py            # Toàn bộ tham số (mặc định = chế độ sản phẩm notebook)
│   ├── schemas.py           # Pydantic request/response
│   ├── routers/detect.py    # GET /health, POST /api/v1/detect
│   └── pipeline/
│       ├── labels.py        # Lọc từ điển/danh từ cụ thể, so khớp nhãn, IoU
│       ├── florence.py      # Nạp Florence-2, nhận diện, khử trùng lặp
│       ├── sam.py           # Nạp SAM, phân đoạn box thành mask
│       ├── clip.py          # CLIP xác thực nhãn nguồn `base`
│       └── pipeline.py      # Điều phối: detect → dedup → CLIP → SAM cắt ảnh
├── checkpoints/             # Checkpoint SAM (tự tải, đã .gitignore)
├── nltk_data/               # Dữ liệu NLTK (tự tải, đã .gitignore)
├── .env / .env.example / .env.notebook
├── requirements.txt
└── Dockerfile
```
