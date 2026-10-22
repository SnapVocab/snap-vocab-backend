package vn.ptit.snapvocab.badge.exception;

import vn.ptit.snapvocab.badge.domain.enumeration.BadgeErrorCode;

public class BadgeException extends RuntimeException {

	private final BadgeErrorCode code;
	private final transient Object details;

	public BadgeException(BadgeErrorCode code, String message, Object details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public BadgeErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}
