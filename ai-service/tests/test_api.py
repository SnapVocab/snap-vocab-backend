"""HTTP contract tests for /api/v1/detect and /health, without loading any model.

The backend branches on `error.code`, so these pin the codes, not just statuses.
"""
import io
import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
from PIL import Image

from app.config import Settings
from app.main import create_app
from app.pipeline.pipeline import ScanResult


def png_bytes(size=(64, 64)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size).save(buf, "PNG")
    return buf.getvalue()


def detection(**overrides):
    base = {
        "label": "coffee mug", "headword": "mug", "score": 0.9, "source": "od",
        "reliability": "HIGH", "box": [1.0, 2.0, 30.0, 40.0],
    }
    return {**base, **overrides}


class DetectApiTests(unittest.TestCase):
    def setUp(self):
        self.app = create_app()
        self.app.state.settings = Settings(device="cpu", warmup_on_load=False, max_upload_mb=1)
        engines = Mock()
        engines.florence.loaded = True
        engines.clip.loaded = False
        engines.device = "cpu"
        self.app.state.engines = engines
        # Default TestClient re-raises server exceptions; the contract is the JSON body.
        self.client = TestClient(self.app, raise_server_exceptions=False)

    def post(self, data=None, content_type="image/png", headers=None):
        data = png_bytes() if data is None else data
        return self.client.post(
            "/api/v1/detect",
            files={"file": ("photo.png", data, content_type)},
            headers=headers or {},
        )

    @patch("app.routers.detect.process_image")
    def test_success_returns_contract_fields(self, process):
        process.return_value = ScanResult([detection()], None, 64, 48)
        res = self.post(headers={"X-Request-Id": "req-123"})
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertEqual(body["request_id"], "req-123")
        self.assertEqual(body["model_version"], self.app.state.settings.model_version)
        self.assertIsInstance(body["processing_time_ms"], int)
        self.assertEqual((body["image_width"], body["image_height"]), (64, 48))
        self.assertEqual(body["detections"][0]["headword"], "mug")
        self.assertEqual(body["detections"][0]["reliability"], "HIGH")
        self.assertEqual(body["detections"][0]["box"], {"x1": 1.0, "y1": 2.0, "x2": 30.0, "y2": 40.0})

    @patch("app.routers.detect.process_image")
    def test_request_id_is_generated_when_absent(self, process):
        process.return_value = ScanResult([], None, 64, 64)
        body = self.post().json()
        self.assertTrue(body["request_id"])

    @patch("app.routers.detect.process_image")
    def test_no_object_is_an_empty_list_not_an_error(self, process):
        process.return_value = ScanResult([], None, 64, 64)
        res = self.post()
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["detections"], [])

    def assert_error(self, res, status, code):
        self.assertEqual(res.status_code, status, res.text)
        self.assertEqual(res.json()["error"]["code"], code)
        self.assertTrue(res.json()["error"]["message"])

    def test_unsupported_type_is_invalid_image(self):
        self.assert_error(self.post(content_type="image/gif"), 415, "INVALID_IMAGE")

    def test_too_large_is_invalid_image(self):
        self.assert_error(self.post(data=b"0" * (1024 * 1024 + 1)), 413, "INVALID_IMAGE")

    def test_undecodable_is_invalid_image(self):
        self.assert_error(self.post(data=b"not an image"), 400, "INVALID_IMAGE")

    def test_missing_file_is_invalid_request(self):
        self.assert_error(self.client.post("/api/v1/detect"), 400, "INVALID_REQUEST")

    def test_models_loading_is_model_not_ready(self):
        self.app.state.engines.florence.loaded = False
        self.assert_error(self.post(), 503, "MODEL_NOT_READY")

    @patch("app.routers.detect.process_image", side_effect=RuntimeError("CUDA out of memory"))
    def test_inference_crash_is_model_error(self, _process):
        self.assert_error(self.post(), 500, "MODEL_ERROR")

    @patch("app.routers.detect.process_image")
    def test_token_required_when_configured(self, process):
        process.return_value = ScanResult([], None, 64, 64)
        self.app.state.settings = Settings(device="cpu", warmup_on_load=False, service_token="s3cret")
        self.assert_error(self.post(), 401, "UNAUTHORIZED")
        self.assert_error(self.post(headers={"X-Service-Token": "wrong"}), 401, "UNAUTHORIZED")
        self.assertEqual(self.post(headers={"X-Service-Token": "s3cret"}).status_code, 200)

    def test_health_reports_model_version(self):
        body = self.client.get("/health").json()
        self.assertEqual(body["status"], "ok")
        self.assertEqual(body["model_version"], self.app.state.settings.model_version)
        self.assertIn("gpu_available", body)


if __name__ == "__main__":
    unittest.main()
