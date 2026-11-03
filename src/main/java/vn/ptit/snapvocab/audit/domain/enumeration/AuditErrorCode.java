package vn.ptit.snapvocab.audit.domain.enumeration;

import org.springframework.http.HttpStatus;

/** admin_audit_log_backend.md §7. */
public enum AuditErrorCode {
	AUDIT_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
	AUDIT_REASON_INVALID(HttpStatus.BAD_REQUEST),
	AUDIT_FILTER_INVALID(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

	AuditErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
