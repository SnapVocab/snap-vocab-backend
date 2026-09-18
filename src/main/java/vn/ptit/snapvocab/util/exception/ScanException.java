package vn.ptit.snapvocab.util.exception;

import vn.ptit.snapvocab.domain.enumeration.ScanErrorCode;

/**
 * A scan failure with a machine-readable {@link ScanErrorCode}. The message is
 * for logs only; the app receives the code.
 */
public class ScanException extends RuntimeException {

	private final ScanErrorCode code;

	public ScanException(ScanErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ScanException(ScanErrorCode code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public ScanErrorCode getCode() {
		return code;
	}
}
