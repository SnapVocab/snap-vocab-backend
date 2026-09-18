"""Tests for the detection pipeline that run without loading Florence-2.

Replaces the old test_lite_pipeline.py, which covered the /api/scan/lite flow
deleted in 68f8a31 and no longer imported.
"""
import unittest
from unittest.mock import Mock, patch

from PIL import Image

from app.config import Settings
from app.pipeline.florence import FlorenceEngine, dedup_detections
from app.pipeline.pipeline import process_image


def settings(**overrides) -> Settings:
    base = {
        "device": "cpu",
        "enable_tiled_od": False,
        "enable_self_grounding": False,
        "enable_dense_caption": False,
        "enable_base_vocab": False,
        "require_dictionary_labels": True,
        "warmup_on_load": False,
    }
    return Settings(**{**base, **overrides})


def det(label, box, score=0.9, source="od"):
    return {"label": label, "score": score, "box": box, "source": source}


class BatchGroupingTests(unittest.TestCase):
    """Florence-2 does not mask padding, so only equal-length prompts may batch."""

    def _engine(self, batch_size, lengths):
        engine = FlorenceEngine(settings(florence_batch_size=batch_size), "cpu", None)
        engine.model = Mock()
        engine.processor = Mock()
        engine.processor.tokenizer = Mock(return_value={"input_ids": [[0] * n for n in lengths]})
        return engine

    def test_equal_length_prompts_share_one_generate_call(self):
        engine = self._engine(batch_size=4, lengths=[3, 3, 3, 3])
        with patch.object(FlorenceEngine, "_generate", return_value=[{}, {}, {}, {}]) as gen:
            engine.run_many([Image.new("RGB", (8, 8))] * 4, "<OD>")
        self.assertEqual(gen.call_count, 1)

    def test_differing_lengths_are_split_into_separate_calls(self):
        engine = self._engine(batch_size=4, lengths=[3, 7, 3])
        with patch.object(FlorenceEngine, "_generate", side_effect=lambda i, p, t: [{}] * len(i)) as gen:
            engine.run_many(
                [Image.new("RGB", (8, 8))] * 3,
                "<CAPTION_TO_PHRASE_GROUNDING>",
                text_inputs=["a.", "a bit longer.", "b."],
            )
        # the two length-3 prompts batch together, the length-7 one goes alone
        self.assertEqual(gen.call_count, 2)
        self.assertEqual(sorted(len(call.args[0]) for call in gen.call_args_list), [1, 2])

    def test_results_keep_input_order_across_buckets(self):
        engine = self._engine(batch_size=4, lengths=[3, 7, 3])
        with patch.object(
            FlorenceEngine, "_generate",
            side_effect=lambda images, prompts, task: [{"p": p} for p in prompts],
        ):
            out = engine.run_many(
                [Image.new("RGB", (8, 8))] * 3, "<T>", text_inputs=["a", "LONGER", "b"],
            )
        self.assertEqual([o["p"] for o in out], ["<T>a", "<T>LONGER", "<T>b"])

    def test_batch_failure_falls_back_to_one_at_a_time(self):
        engine = self._engine(batch_size=4, lengths=[3, 3])
        calls = []

        def flaky(images, prompts, task):
            calls.append(len(images))
            if len(images) > 1:
                raise RuntimeError("size of tensor a must match tensor b")
            return [{}]

        with patch.object(FlorenceEngine, "_generate", side_effect=flaky):
            out = engine.run_many([Image.new("RGB", (8, 8))] * 2, "<OD>")
        self.assertEqual(calls, [2, 1, 1])
        self.assertEqual(len(out), 2)

    def test_single_prompt_failure_propagates(self):
        engine = self._engine(batch_size=4, lengths=[3])
        with patch.object(FlorenceEngine, "_generate", side_effect=RuntimeError("boom")):
            with self.assertRaises(RuntimeError):
                engine.run_many([Image.new("RGB", (8, 8))], "<OD>")


class DedupTests(unittest.TestCase):
    def test_same_label_overlapping_boxes_collapse(self):
        out = dedup_detections([
            det("cup", [0, 0, 100, 100], score=0.9),
            det("cup", [5, 5, 100, 100], score=0.8),
        ])
        self.assertEqual(len(out), 1)
        self.assertEqual(out[0]["score"], 0.9)

    def test_same_label_apart_boxes_both_survive(self):
        out = dedup_detections([
            det("cup", [0, 0, 50, 50]),
            det("cup", [500, 500, 550, 550]),
        ])
        self.assertEqual(len(out), 2)

    def test_related_labels_need_higher_overlap_than_identical_ones(self):
        # person/man are aliases: IoU 0.6 is above the same-label bar (0.5) but
        # below the related-label bar (0.7), so both must survive.
        boxes = [det("person", [0, 0, 100, 100], score=0.9), det("man", [0, 0, 100, 62], score=0.8)]
        self.assertEqual(len(dedup_detections(boxes)), 2)


class ProcessImageTests(unittest.TestCase):
    def _run(self, detections, **overrides):
        florence = Mock()
        florence.detect.return_value = detections
        clip = Mock()
        clip.loaded = False
        return process_image(settings(**overrides), florence, clip, Image.new("RGB", (200, 200)))

    def test_tiny_and_giant_boxes_are_dropped(self):
        result = self._run([
            det("cup", [0, 0, 4, 4]),            # 0.02% of the image -> too small
            det("wall", [0, 0, 200, 200]),       # 100% -> backdrop
            det("bottle", [10, 10, 90, 90]),     # 16% -> kept
        ])
        self.assertEqual([d["label"] for d in result.detections], ["bottle"])

    def test_non_dictionary_labels_are_dropped_before_dedup(self):
        result = self._run([det("bottle", [10, 10, 90, 90]), det("qwertyx", [100, 100, 180, 180])])
        self.assertEqual([d["label"] for d in result.detections], ["bottle"])

    def test_one_box_per_label_highest_score_wins(self):
        result = self._run([
            det("bottle", [10, 10, 90, 90], score=0.7),
            det("bottle", [100, 100, 180, 180], score=0.9),
        ])
        self.assertEqual(len(result.detections), 1)
        self.assertEqual(result.detections[0]["score"], 0.9)

    def test_annotated_image_present_by_default(self):
        result = self._run([det("bottle", [10, 10, 90, 90])])
        self.assertIsInstance(result.annotated_image, bytes)
        self.assertGreater(len(result.annotated_image), 0)

    def test_annotated_image_skipped_when_disabled(self):
        result = self._run([det("bottle", [10, 10, 90, 90])], return_annotated_image=False)
        self.assertIsNone(result.annotated_image)

    def test_result_dimensions_match_the_resized_image(self):
        result = self._run([det("bottle", [10, 10, 90, 90])], max_input_size=100)
        self.assertEqual((result.width, result.height), (100, 100))

    def test_clip_verifies_only_base_vocabulary_boxes(self):
        florence = Mock()
        florence.detect.return_value = [
            det("bottle", [10, 10, 90, 90], source="od"),
            det("laptop", [100, 100, 180, 180], source="base"),
        ]
        clip = Mock()
        clip.loaded = True
        clip.verify.return_value = ([], [])  # CLIP rejects the hallucinated base box
        result = process_image(settings(), florence, clip, Image.new("RGB", (200, 200)))
        self.assertEqual([d["label"] for d in result.detections], ["bottle"])
        verified = clip.verify.call_args.args[1]
        self.assertEqual([d["source"] for d in verified], ["base"])


class ResponseTests(unittest.TestCase):
    def test_response_allows_a_missing_annotated_image(self):
        from app.schemas import DetectResponse

        payload = DetectResponse(detections=[], labels=[], image_width=10, image_height=10)
        self.assertIsNone(payload.annotated_image_base64)
        self.assertIsNone(payload.annotated_image_mime)


if __name__ == "__main__":
    unittest.main()
