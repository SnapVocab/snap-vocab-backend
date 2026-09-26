package vn.ptit.snapvocab.scan.domain.enumeration;

import vn.ptit.snapvocab.common.dto.RestResponse;

import org.springframework.http.HttpStatus;

/**
 * Why a scan failed. The name is sent to the app as {@code RestResponse.error}
 * (same convention as {@code USER_NOT_ACTIVATED}) and as {@code errorCode} when
 * polling a scan, so the app can branch on it; treat renames as breaking changes.
 */
public enum ScanErrorCode {

	/** Not an image, unsupported type, too large, undecodable, or not the caller's upload. */
	INVALID_IMAGE(HttpStatus.BAD_REQUEST),
	/** Daily scan limit reached; details carry remaining count and reset time. */
	QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
	/** Recognition queue is full; nothing was charged, retry later. */
	AI_QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE),
	/** AI service unreachable, or still loading its models. */
	AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	/** AI service did not answer within {@code ai-service.read-timeout-ms}. */
	AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
	/** AI service failed while running the model, or answered something unusable. */
	AI_ERROR(HttpStatus.BAD_GATEWAY),
	/** The job was lost (server restart) or outlived any plausible run time. */
	INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ScanErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}

