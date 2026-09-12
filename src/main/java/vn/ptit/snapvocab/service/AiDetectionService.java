package vn.ptit.snapvocab.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.AiLiteDetectResponseDTO;

public interface AiDetectionService {

	AiDetectResponseDTO detect(MultipartFile image);

	/**
	 * Lite detection: normalized boxes and labels only, with no annotated image,
	 * so the response carries no image bytes at all.
	 */
	AiLiteDetectResponseDTO detectLite(MultipartFile image);
}
