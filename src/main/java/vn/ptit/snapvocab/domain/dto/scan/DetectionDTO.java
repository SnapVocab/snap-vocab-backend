package vn.ptit.snapvocab.domain.dto.scan;

/**
 * Mirrors the AI service's {@code Detection} schema (ai-service/app/schemas.py).
 */
public record DetectionDTO(
		String label,
		double score,
		String source,
		BoundingBoxDTO box
) {
}
