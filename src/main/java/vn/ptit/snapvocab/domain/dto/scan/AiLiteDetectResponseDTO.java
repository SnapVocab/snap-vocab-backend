package vn.ptit.snapvocab.domain.dto.scan;

import java.util.List;

/** Mirrors the AI service's lite detection response. */
public record AiLiteDetectResponseDTO(
		String mode,
		ImageSizeDTO image,
		List<String> labels,
		List<LiteDetectionDTO> detections
) {
}
