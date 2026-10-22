package vn.ptit.snapvocab.audit.exception;

import vn.ptit.snapvocab.audit.domain.enumeration.AuditErrorCode;

public class AuditException extends RuntimeException {

	private final AuditErrorCode code;
	private final transient Object details;

	public AuditException(AuditErrorCode code, String message, Object details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public AuditErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}
