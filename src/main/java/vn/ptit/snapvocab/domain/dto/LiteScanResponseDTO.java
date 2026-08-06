package vn.ptit.snapvocab.domain.dto;

import java.util.List;

public record LiteScanResponseDTO(
		String mode,
		ImageSizeDTO image,
		List<String> labels,
		List<ScanWordDTO> words,
		List<LiteScanDetectionDTO> detections
) {
}
