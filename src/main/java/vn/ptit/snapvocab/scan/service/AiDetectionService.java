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

	/**
	 * Sends a presigned storage URL instead of uploading bytes through the backend.
	 *
	 * @param requestId sent as X-Request-Id so AI-side logs line up with ours
	 * @throws vn.ptit.snapvocab.scan.exception.ScanException on any failure
	 */
	AiDetectResponseDTO detectByUrl(String imageUrl, String requestId);

	default boolean isAsyncEnabled() {
		return false;
	}

	/**
	 * Dispatches a scan job asynchronously to the AI service with the configured webhook callback URL.
	 *
	 * @param requestId sent as request_id and X-Request-Id
	 * @throws vn.ptit.snapvocab.scan.exception.ScanException on dispatch failure
	 */
	void submitByUrl(String imageUrl, String requestId);
}

