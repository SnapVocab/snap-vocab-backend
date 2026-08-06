package vn.ptit.snapvocab.domain.dto;

public record LiteScanDetectionDTO(
		String label,
		String source,
		NormalizedBoundingBoxDTO box
) {
}
