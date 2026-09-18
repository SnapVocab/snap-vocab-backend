# SnapVocab AI Service

Service FastAPI nhận diện đồ vật trong ảnh và trả về tên tiếng Anh của chúng,
để backend tra nghĩa và app dựng thẻ từ vựng.

## Pipeline

```
Ảnh đầu vào
  → Resize (cạnh dài tối đa MAX_INPUT_SIZE)
  → Florence-2 chạy <OD>                    (1 lượt gọi model)
  → Loại box quá nhỏ / quá lớn              ┐ lọc rẻ trước, vì NMS ở bước
  → Loại nhãn không có trong từ điển        ┘ sau là O(n²) kèm tra WordNet
  → NMS 2 tầng + mỗi nhãn giữ 1 box
  → Vẽ tất cả box lên MỘT ảnh duy nhất      (bỏ qua nếu RETURN_ANNOTATED_IMAGE=false)
  → Trả: danh sách từ + box + ảnh đã khoanh vùng
```

Được port từ notebook nghiên cứu `object_scan_florence2` nhưng đã rút gọn mạnh
để chạy được với độ trễ chấp nhận được trên CPU. Các bước bổ sung của notebook
(tiled OD, self-grounding, dense caption, từ vựng nền + CLIP) vẫn còn trong code
nhưng **mặc định tắt** — xem [Cấu hình](#cấu-hình). Bước cắt nền bằng SAM đã bị
gỡ hẳn: luồng hiện tại không sinh ảnh cắt nền từng vật nữa.

### Gộp lô lời gọi model (batching)

Các bước lặp lại cùng một task prompt được gộp vào chung một lượt `generate()`:
tiled OD (4 lượt) gộp trọn vẹn, từ vựng nền gộp theo nhóm prompt cùng độ dài.

> ⚠️ **Chỉ prompt dài BẰNG NHAU mới gộp được.** `Florence2ForConditionalGeneration`
> tự dựng attention mask bằng `torch.ones(...)` rồi vứt đi, và chuyển thẳng
> `attention_mask` của ta xuống language model — vừa gây lỗi lệch shape, vừa
> khiến token PAD bị coi như từ thật. Chi tiết ở docstring
> `FlorenceEngine._generate`. Nếu một lô lỗi, service tự lùi về chạy tuần tự
> nên request không hỏng, chỉ chậm hơn.

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

Lần chạy đầu sẽ tải model (Florence-2, CLIP,
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
  "labels": ["bottle", "person"],
  "detections": [
    {
      "label": "bottle",
      "score": 0.9,
      "source": "od",
      "box": { "x1": 745.0, "y1": 435.0, "x2": 785.0, "y2": 542.0 }
    }
  ],
  "annotated_image_base64": "UklGRiQAAABXRUJQVlA4...",
  "annotated_image_mime": "image/webp",
  "image_width": 900,
  "image_height": 599
}
```

- `annotated_image_base64` là **một ảnh duy nhất** đã vẽ sẵn toàn bộ khung +
  nhãn + độ tin cậy. Chuỗi **không** có tiền tố `data:` — client tự ghép:
  `data:${annotated_image_mime};base64,${annotated_image_base64}`.
- `image_width`/`image_height` là **hệ toạ độ chung** của cả ảnh khoanh vùng
  lẫn mọi `box`, nên client không phải quy đổi tỉ lệ.
- Không còn ảnh cắt riêng cho từng vật thể (trước đây khiến response phình
  lên ~2MB cho ảnh vào 193KB).
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

### Hiệu năng: hai tham số quan trọng nhất

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `FLORENCE_NUM_BEAMS` | `3` | **Đừng hạ xuống 1.** Đo trên `hell.webp` (CPU, Florence-2-base): 3 → 1 nhanh hơn 24% (45.4s → 34.6s) nhưng số nhãn đúng rơi từ 13/33 xuống 7/33, và model bịa ra vật không có trong ảnh. Một ghi chú cũ trong repo nói chỉ mất ~3% — số đo cho thấy không phải vậy. |
| `FLORENCE_BATCH_SIZE` | `4` | Số prompt gộp chung một lượt `generate()`. Bề rộng decode thực tế = `BATCH_SIZE × NUM_BEAMS`. Gặp CUDA OOM thì hạ biến này, đừng hạ beam. |

### Các cờ quan trọng

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DEVICE` | `cuda` | `cuda` \| `cpu` \| `auto` |
| `FLORENCE_MODEL_ID` | `microsoft/Florence-2-base` | `Florence-2-large` chính xác hơn nhưng chậm vài lần |
| `MAX_INPUT_SIZE` | `900` | Cạnh dài tối đa sau resize; cũng là hệ toạ độ của box |
| `MIN_BOX_AREA_RATIO` | `0.004` | Bỏ box nhỏ hơn 0.4% diện tích ảnh (nhiễu) |
| `MAX_BOX_AREA_RATIO` | `0.85` | Bỏ box trùm >85% ảnh (phông nền, không đáng học) |
| `ANNOTATED_FORMAT` | `WEBP` | Định dạng ảnh khoanh vùng (WEBP nhỏ hơn PNG ~15×) |
| `ANNOTATED_QUALITY` | `85` | Chất lượng nén WebP |
| `ENABLE_TILED_OD` | `false` | Quét thêm 4 ô để bắt vật nhỏ (**+4 lượt gọi**) |
| `ENABLE_SELF_GROUNDING` | `false` | Tự mô tả ảnh rồi ground caption (**+2 lượt**) |
| `ENABLE_DENSE_CAPTION` | `false` | Dense region caption (**+1 lượt**) |
| `ENABLE_BASE_VOCAB` | `false` | Quét 80 từ vựng nền (**+7 lượt**, kéo theo CLIP) |
| `ENABLE_EXTRA_VOCAB` | `false` | Thêm 79 từ gia dụng vào bộ nền (**+7 lượt nữa**) |
| `MAX_UPLOAD_MB` | `15` | Giới hạn dung lượng ảnh |

SAM chỉ dùng để cắt nền từng vật thể. Luồng hiện tại không sinh ảnh cắt nền nữa
nên `sam.py` đã bị **gỡ khỏi codebase** cùng hai dependency chỉ nó dùng
(`segment-anything`, `opencv-python-headless`). Nếu còn file
`checkpoints/sam_vit_*.pth` trên máy thì xoá được — không gì đọc chúng nữa.
CLIP cũng chỉ nạp khi `ENABLE_BASE_VOCAB=true`.

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

**Thời gian gần như tỉ lệ thuận với số lượt gọi Florence-2** (~7-13 giây/lượt
trên CPU i5-10300H).

Số đo thật trên `hell.webp` (990×659, ảnh bếp nhiều đồ vật), `Florence-2-base`.
Cột cuối là số nhãn đúng so với 33 vật thể nhìn thấy được trong ảnh — chạy lại
bằng `evaluate_configs.py`:

| Cấu hình | Thời gian | Số nhãn | Đúng/33 |
|---|---:|---:|---:|
| `OD` một mình | 11.7s | 4 | 4 |
| `OD` + `DENSE_CAPTION` | 18.5s | 4 | 4 |
| `OD` + `TILED_OD` @900px | 31.1s | 5 | 5 |
| **`OD` + `SELF_GROUNDING` (mặc định)** | **~40s** | **16** | **13** |
| `OD` + `SELF` + `TILED` @900px | 71.8s | 17 | 14 |
| `OD` + `SELF` + `TILED` @1400px | 71.9s | 18 | 15 |
| `OD` + `SELF` + `TILED` + `BASE_VOCAB` @1400px | 131.9s | 29 | 19 |
| `OD` + `SELF` + `BASE` + `EXTRA_VOCAB` | 114.6s | 29 | 16 |

Bốn điều rút ra từ số đo:

1. **`SELF_GROUNDING` là bước đáng giá nhất**: gấp 4 lần số nhãn (4 → 16) chỉ
   với khoảng +22 giây. Đây là lý do nó được bật mặc định.
2. **Chất lượng ảnh đầu vào quan trọng hơn tưởng tượng**: cùng ảnh đó nhưng
   nén lại JPEG 80% (đúng như `ImagePicker` của app từng làm) chỉ còn **11
   nhãn thay vì 16**. Vì vậy app phải gửi ảnh ở `quality: 1`.
3. **`MAX_INPUT_SIZE` cao không tốn thêm thời gian** (Florence-2 vẫn co về
   768px bên trong) nhưng giúp `TILED_OD` bắt được vật nhỏ — nâng 900 → 1400
   lấy lại `faucet` và `drawer`. Đã đặt mặc định 1400.
4. **`BASE_VOCAB` tăng độ phủ nhưng giảm độ chính xác**: 29 nhãn nhưng chỉ 19
   đúng (~35% là nhãn sai như `laptop`, `cake`, `sandwich`, `book`). Bật khi
   cần phủ rộng và chấp nhận người dùng phải bỏ chọn bớt.
   `EXTRA_VOCAB` thì **làm tệ hơn** (16 đúng, chậm hơn) — không nên bật.

Để so sánh, pipeline cũ (đầy đủ các bước + cắt nền SAM) mất **165 giây** và
trả về response **2196 KB**; mặc định hiện tại mất **~34 giây** và **~193 KB**.

### Những từ chưa cấu hình nào bắt được

`baby`, `carrot`, `jar`, `kale`, `microwave`, `mug`, `scale`, `shirt`, `sink`,
`spatula`, `toy`, `turkey`, `window` — chủ yếu là vật nhỏ hoặc bị che khuất.
Muốn bắt thêm thì cần `Florence-2-large` (chậm hơn vài lần trên CPU, nên chỉ
khả thi khi có GPU).

**Cảnh báo timeout**: app đặt timeout 180 giây
(`src/features/scan/api.ts`), backend đặt `read-timeout-ms: 600000`
(`application-dev.yaml`). Nếu AI xử lý lâu hơn, app sẽ báo lỗi **dù AI vẫn
đang chạy bình thường**. Muốn dùng cấu hình đầy đủ trên CPU thì phải nâng cả
hai timeout này lên.

## Sự cố thường gặp

**Service crash ngay khi khởi động, không có traceback (exit code 139 / segfault)**
→ Hết RAM. `Florence-2-large` trên CPU/fp32 cần khoảng 6-7GB.
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
│   ├── engines.py           # Giữ singleton Florence-2 / CLIP
│   ├── config.py            # Toàn bộ tham số (mặc định = chế độ sản phẩm notebook)
│   ├── schemas.py           # Pydantic request/response
│   ├── routers/detect.py    # GET /health, POST /api/v1/detect
│   └── pipeline/
│       ├── labels.py        # Lọc từ điển/danh từ cụ thể, so khớp nhãn, IoU
│       ├── florence.py      # Nạp Florence-2, nhận diện, khử trùng lặp
│       ├── annotate.py      # Vẽ box + nhãn lên một ảnh, nén WebP
│       ├── timing.py        # Đo thời gian từng bước (xem log khi chạy)
│       ├── clip.py          # (chỉ khi bật BASE_VOCAB) xác thực nhãn nền
│       └── pipeline.py      # Điều phối toàn bộ luồng
├── nltk_data/               # Dữ liệu NLTK (tự tải, đã .gitignore)
├── .env / .env.example / .env.notebook
├── requirements.txt
└── Dockerfile
```
