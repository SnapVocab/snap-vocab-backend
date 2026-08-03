package vn.ptit.snapvocab.domain.dto;

import java.util.List;

/**
 * Mirrors the AI service's {@code DetectResponse} schema (ai-service/app/schemas.py).
 */
public record AiDetectResponseDTO(
		List<DetectionDTO> detections,
		List<String> labels
) {
}
