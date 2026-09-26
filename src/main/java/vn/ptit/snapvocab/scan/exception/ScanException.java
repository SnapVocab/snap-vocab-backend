package vn.ptit.snapvocab.scan.exception;

import vn.ptit.snapvocab.common.dto.RestResponse;

import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;

/**
 * A scan failure with a machine-readable {@link ScanErrorCode}. The message is
 * for logs only; the app receives the code, plus {@code details} as
 * {@code RestResponse.data} when present (e.g. quota numbers).
 */
public class ScanException extends RuntimeException {

	private final ScanErrorCode code;
	private final transient Object details;

	public ScanException(ScanErrorCode code, String message) {
		this(code, message, null, null);
	}

	public ScanException(ScanErrorCode code, String message, Throwable cause) {
		this(code, message, null, cause);
	}

	public ScanException(ScanErrorCode code, String message, Object details, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.details = details;
	}

	public ScanErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}

