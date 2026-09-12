package vn.ptit.snapvocab.domain.dto.scan;

public record LiteScanDetectionDTO(
		String label,
		String source,
		NormalizedBoundingBoxDTO box
) {
}
