package vn.ptit.snapvocab.scan.service;

import vn.ptit.snapvocab.scan.exception.ScanException;

import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;

public interface AiDetectionService {

	/**
	 * @param requestId sent as X-Request-Id so AI-side logs line up with ours
	 * @throws vn.ptit.snapvocab.scan.exception.ScanException on any failure
	 */
	AiDetectResponseDTO detect(ScanImage image, String requestId);
}

