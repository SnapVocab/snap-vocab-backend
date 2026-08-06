import asyncio
import io
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from PIL import Image

from app.config import Settings
from app.engines import Engines
from app.pipeline.labels import extract_concrete_nouns
from app.pipeline.lite import LiteDetectionPipeline, LiteDetectionResult
from app.routers.detect import detect, detect_lite, health

EMPTY_OUTPUT = {"bboxes": [], "labels": []}


def settings(mode: str = "lite", **overrides) -> Settings:
    values = {
        "pipeline_mode": mode,
        "device": "cpu",
        "lite_nms_iou_threshold": 0.5,
        "lite_max_detections": 20,
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def image(width: int = 100, height: int = 50) -> Image.Image:
    return Image.new("RGB", (width, height), "white")


class FakeUpload:
    content_type = "image/png"

    def __init__(self, source: Image.Image) -> None:
        buffer = io.BytesIO()
        source.save(buffer, format="PNG")
        self.raw = buffer.getvalue()

    async def read(self) -> bytes:
        return self.raw


class DenseNounExtractionTests(unittest.TestCase):
    TAGS = {
        "a dog holding fried chicken": [
            ("a", "DT"), ("dog", "NN"), ("holding", "VBG"),
            ("fried", "VBN"), ("chicken", "NN"),
        ],
        "a bowl of beef noodles": [
            ("a", "DT"), ("bowl", "NN"), ("of", "IN"),
            ("beef", "NN"), ("noodles", "NNS"),
        ],
        "a dog wearing a golden crown": [
            ("a", "DT"), ("dog", "NN"), ("wearing", "VBG"),
            ("a", "DT"), ("golden", "JJ"), ("crown", "NN"),
        ],
        "a package of instant noodles": [
            ("a", "DT"), ("package", "NN"), ("of", "IN"),
            ("instant", "JJ"), ("noodles", "NNS"),
        ],
    }

    def test_dense_phrases_become_concrete_noun_heads(self):
        def tag(tokens):
            return self.TAGS[" ".join(tokens)]

        with patch("app.pipeline.labels.nltk.pos_tag", side_effect=tag), patch(
            "app.pipeline.labels.is_concrete_object_word", return_value=True
        ):
            self.assertEqual(extract_concrete_nouns("a dog holding fried chicken"), ["dog", "chicken"])
            self.assertEqual(extract_concrete_nouns("a bowl of beef noodles"), ["bowl", "noodles"])
            self.assertEqual(extract_concrete_nouns("a dog wearing a golden crown"), ["dog", "crown"])
            self.assertEqual(extract_concrete_nouns("a package of instant noodles"), ["package", "noodles"])

    def test_untrusted_nouns_are_not_invented(self):
        with patch("app.pipeline.labels.nltk.pos_tag", return_value=[("mysterious", "JJ"), ("blorb", "NN")]), patch(
            "app.pipeline.labels.is_concrete_object_word", return_value=False
        ):
            self.assertEqual(extract_concrete_nouns("mysterious blorb"), [])


class LitePipelineTests(unittest.TestCase):
    def make_pipeline(self, od_output=None, dense_output=None, **overrides):
        florence = Mock()
        florence.run.side_effect = [od_output or EMPTY_OUTPUT, dense_output or EMPTY_OUTPUT]
        return LiteDetectionPipeline(settings(**overrides), florence), florence

    def test_calls_od_and_dense_once_each_and_ignores_full_flags(self):
        pipeline, florence = self.make_pipeline(
            {"bboxes": [[0, 0, 10, 10]], "labels": [" Cup "]},
            enable_tiled_od=True,
            enable_self_grounding=True,
            enable_base_vocab=True,
            enable_extra_vocab=True,
        )
        pipeline.process(image())

        self.assertEqual(florence.run.call_count, 2)
        self.assertEqual(
            [item.args[1] for item in florence.run.call_args_list],
            ["<OD>", "<DENSE_REGION_CAPTION>"],
        )
        florence.detect.assert_not_called()

    def test_pipeline_mode_defaults_to_full(self):
        self.assertEqual(Settings.model_fields["pipeline_mode"].default, "full")

    def test_normalizes_coordinates_and_clamps_to_original_image(self):
        pipeline, _ = self.make_pipeline({
            "bboxes": [[25, 10, 75, 40], [-20, -10, 120, 60]],
            "labels": ["  LAPTOP  ", "cup"],
        })
        result = pipeline.process(image())
        self.assertEqual(result[0], LiteDetectionResult("laptop", "od", (0.25, 0.2, 0.5, 0.6)))
        self.assertEqual(result[1].box, (0.0, 0.0, 1.0, 1.0))

    def test_rejects_invalid_boxes_from_both_tasks(self):
        invalid = {
            "bboxes": [[1, 2, 3], [1, 2, float("nan"), 4], [10, 10, 10, 20]],
            "labels": ["short", "nan", "zero"],
        }
        pipeline, _ = self.make_pipeline(invalid, invalid)
        with patch("app.pipeline.lite.extract_concrete_nouns", return_value=["chair"]):
            self.assertEqual(pipeline.process(image()), [])

    def test_keeps_tiny_valid_box(self):
        pipeline, _ = self.make_pipeline(
            {"bboxes": [[99, 49, 100, 50]], "labels": ["chair"]}
        )
        self.assertEqual(pipeline.process(image())[0].box, (0.99, 0.98, 0.01, 0.02))

    def test_od_wins_same_label_overlap_with_dense(self):
        pipeline, _ = self.make_pipeline(
            {"bboxes": [[0, 0, 50, 40]], "labels": ["dog"]},
            {"bboxes": [[1, 1, 51, 41]], "labels": ["a dog"]},
        )
        with patch("app.pipeline.lite.extract_concrete_nouns", return_value=["dog"]):
            result = pipeline.process(image())
        self.assertEqual(result, [LiteDetectionResult("dog", "od", (0.0, 0.0, 0.5, 0.8))])

    def test_same_label_at_different_positions_survives(self):
        pipeline, _ = self.make_pipeline(
            {"bboxes": [[0, 0, 10, 10]], "labels": ["dog"]},
            {"bboxes": [[80, 30, 90, 40]], "labels": ["a dog"]},
        )
        with patch("app.pipeline.lite.extract_concrete_nouns", return_value=["dog"]):
            result = pipeline.process(image())
        self.assertEqual([item.source for item in result], ["od", "dense"])

    def test_different_labels_from_same_dense_region_survive(self):
        pipeline, _ = self.make_pipeline(
            dense_output={"bboxes": [[10, 10, 40, 40]], "labels": ["a dog wearing a crown"]}
        )
        with patch("app.pipeline.lite.extract_concrete_nouns", return_value=["dog", "crown"]):
            result = pipeline.process(image())
        self.assertEqual([(item.label, item.source) for item in result], [("dog", "dense"), ("crown", "dense")])
        self.assertEqual(result[0].box, result[1].box)

    def test_max_detections_applies_after_nms(self):
        pipeline, _ = self.make_pipeline({
            "bboxes": [[i * 10, 0, i * 10 + 5, 5] for i in range(5)],
            "labels": [f"item-{i}" for i in range(5)],
        }, lite_max_detections=3)
        self.assertEqual(len(pipeline.process(image())), 3)

    def test_logs_latency_and_counts_without_image_data(self):
        pipeline, _ = self.make_pipeline({"bboxes": [[0, 0, 10, 10]], "labels": ["cup"]})
        with self.assertLogs("app.pipeline.lite", level="INFO") as logs:
            pipeline.process(image())
        message = logs.output[0]
        for field in (
            "od_ms=", "dense_ms=", "postprocess_ms=", "total_ms=",
            "od_detection_count=1", "dense_detection_count=0", "final_detection_count=1",
        ):
            self.assertIn(field, message)
        self.assertNotIn("base64", message.lower())


class EngineModeTests(unittest.TestCase):
    @patch("app.engines.ensure_nltk_data")
    @patch("app.engines.ClipEngine")
    @patch("app.engines.SamEngine")
    @patch("app.engines.FlorenceEngine")
    def test_lite_initializes_and_loads_only_florence(
        self, florence_cls, sam_cls, clip_cls, ensure_nltk
    ):
        florence_cls.return_value.loaded = True
        engines = Engines(settings())
        engines.load_all()

        florence_cls.return_value.load.assert_called_once()
        sam_cls.assert_not_called()
        clip_cls.assert_not_called()
        ensure_nltk.assert_called_once()
        self.assertIsNone(engines.sam)
        self.assertIsNone(engines.clip)
        self.assertTrue(engines.ready)

    @patch("app.engines.ensure_nltk_data")
    @patch("app.engines.ClipEngine")
    @patch("app.engines.SamEngine")
    @patch("app.engines.FlorenceEngine")
    def test_full_still_initializes_and_loads_all_engines(
        self, florence_cls, sam_cls, clip_cls, ensure_nltk
    ):
        engines = Engines(settings("full"))
        engines.load_all()
        florence_cls.return_value.load.assert_called_once()
        sam_cls.return_value.load.assert_called_once()
        clip_cls.return_value.load.assert_called_once()
        ensure_nltk.assert_called_once()


class EndpointContractTests(unittest.TestCase):
    def request_for(self, engines):
        return SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(engines=engines)))

    def test_lite_response_has_sources_unique_labels_and_no_image_payload(self):
        pipeline = Mock()
        pipeline.process.return_value = [
            LiteDetectionResult("dog", "od", (0.1, 0.2, 0.01, 0.02)),
            LiteDetectionResult("dog", "od", (0.7, 0.2, 0.01, 0.02)),
            LiteDetectionResult("crown", "dense", (0.3, 0.4, 0.2, 0.3)),
        ]
        engines = SimpleNamespace(ready=True, settings=settings(), pipeline=pipeline)
        with patch("app.routers.detect.base64.b64encode") as encode:
            response = asyncio.run(detect(self.request_for(engines), FakeUpload(image())))
        encode.assert_not_called()
        payload = response.model_dump()

        self.assertEqual(payload["labels"], ["dog", "crown"])
        self.assertEqual(payload["detections"][0]["source"], "od")
        self.assertEqual(payload["detections"][2]["source"], "dense")
        serialized = response.model_dump_json().lower()
        for forbidden in ("base64", "mask", "crop", "url"):
            self.assertNotIn(forbidden, serialized)
        self.assertNotIn("score", payload["detections"][0])

    def test_explicit_lite_endpoint_uses_lite_pipeline_in_full_mode(self):
        lite_pipeline = Mock()
        lite_pipeline.process.return_value = [
            LiteDetectionResult("cup", "od", (0.1, 0.2, 0.3, 0.4)),
        ]
        default_pipeline = Mock()
        engines = SimpleNamespace(
            settings=settings("full"),
            florence=SimpleNamespace(loaded=True),
            lite_pipeline=lite_pipeline,
            pipeline=default_pipeline,
        )
        response = asyncio.run(detect_lite(self.request_for(engines), FakeUpload(image())))
        self.assertEqual(response.mode, "lite")
        lite_pipeline.process.assert_called_once()
        default_pipeline.process.assert_not_called()

    def test_health_is_mode_aware(self):
        lite = SimpleNamespace(
            ready=True, settings=settings(), device="cuda",
            florence=SimpleNamespace(loaded=True), sam=None, clip=None,
        )
        self.assertEqual(health(self.request_for(lite)).model_dump(), {
            "status": "ok", "mode": "lite", "device": "cuda",
            "florence_loaded": True, "sam_loaded": False, "clip_loaded": False,
        })

    def test_full_response_contract_is_unchanged(self):
        pipeline = Mock()
        pipeline.process.return_value = [SimpleNamespace(
            label="cup", score=0.9, source="od",
            box=[1.0, 2.0, 30.0, 40.0], crop_png=b"png",
        )]
        engines = SimpleNamespace(ready=True, settings=settings("full"), pipeline=pipeline)
        response = asyncio.run(detect(self.request_for(engines), FakeUpload(image())))
        payload = response.model_dump()
        self.assertEqual(set(payload), {"detections", "labels"})
        self.assertEqual(payload["detections"][0]["image_base64"], "cG5n")


if __name__ == "__main__":
    unittest.main()
