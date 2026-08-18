package vn.ptit.snapvocab.domain.dto.scan;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Mirrors the AI service's {@code DetectResponse} schema (ai-service/app/schemas.py).
 */
public record AiDetectResponseDTO(
		List<DetectionDTO> detections,
		List<String> labels,
		@JsonProperty("annotated_image_base64") String annotatedImageBase64,
		@JsonProperty("annotated_image_mime") String annotatedImageMime,
		@JsonProperty("image_width") int imageWidth,
		@JsonProperty("image_height") int imageHeight
) {
}
