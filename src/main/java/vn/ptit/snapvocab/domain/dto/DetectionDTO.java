package vn.ptit.snapvocab.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the AI service's {@code Detection} schema (ai-service/app/schemas.py).
 */
public record DetectionDTO(
		String label,
		double score,
		String source,
		BoundingBoxDTO box,
		@JsonProperty("image_base64") String imageBase64
) {
}
