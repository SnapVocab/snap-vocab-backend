package vn.ptit.snapvocab.service;

import vn.ptit.snapvocab.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanImage;

public interface AiDetectionService {

	/**
	 * @param requestId sent as X-Request-Id so AI-side logs line up with ours
	 * @throws vn.ptit.snapvocab.util.exception.ScanException on any failure
	 */
	AiDetectResponseDTO detect(ScanImage image, String requestId);
}
