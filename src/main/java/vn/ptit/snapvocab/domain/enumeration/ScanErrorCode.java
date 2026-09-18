package vn.ptit.snapvocab.domain.enumeration;

import org.springframework.http.HttpStatus;

/**
 * Why a scan failed. The name is sent to the app as {@code RestResponse.error}
 * (same convention as {@code USER_NOT_ACTIVATED}), so the app can branch on it;
 * treat renames as breaking changes.
 */
public enum ScanErrorCode {

	/** Not an image, unsupported type, too large, or undecodable. */
	INVALID_IMAGE(HttpStatus.BAD_REQUEST),
	/** AI service unreachable, or still loading its models. */
	AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	/** AI service did not answer within {@code ai-service.read-timeout-ms}. */
	AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
	/** AI service failed while running the model, or answered something unusable. */
	AI_ERROR(HttpStatus.BAD_GATEWAY);

	private final HttpStatus status;

	ScanErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
