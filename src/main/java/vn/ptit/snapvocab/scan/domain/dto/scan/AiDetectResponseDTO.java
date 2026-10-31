package vn.ptit.snapvocab.scan.domain.dto.scan;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Mirrors the AI service's {@code DetectResponse} schema (snap-vocab-ai: app/schemas.py).
 */
public record AiDetectResponseDTO(
		@JsonProperty("request_id") String requestId,
		@JsonProperty("model_version") String modelVersion,
		@JsonProperty("processing_time_ms") long processingTimeMs,
		List<DetectionDTO> detections,
		List<String> labels,
		@JsonProperty("annotated_image_base64") String annotatedImageBase64,
		@JsonProperty("annotated_image_mime") String annotatedImageMime,
		@JsonProperty("image_width") int imageWidth,
		@JsonProperty("image_height") int imageHeight
) {
}

