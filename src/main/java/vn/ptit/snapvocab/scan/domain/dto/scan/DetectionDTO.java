package vn.ptit.snapvocab.scan.domain.dto.scan;

/**
 * Mirrors the AI service's {@code Detection} schema (ai-service/app/schemas.py).
 *
 * @param headword   singular last word of {@code label}, the dictionary fallback
 *                   when {@code label} itself has no entry
 * @param score      fixed per-source constant for ranking, not a confidence
 * @param reliability HIGH / MEDIUM / LOW, derived from {@code source}
 */
public record DetectionDTO(
		String label,
		String headword,
		double score,
		String source,
		String reliability,
		BoundingBoxDTO box
) {
}

