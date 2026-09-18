package vn.ptit.snapvocab.domain.dto.scan;

/**
 * Mirrors the AI service's error body: {@code {"error": {"code", "message"}}}.
 */
public record AiErrorResponseDTO(Detail error) {

	public record Detail(String code, String message) {
	}
}
