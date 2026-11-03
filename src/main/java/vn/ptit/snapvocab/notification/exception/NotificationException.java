package vn.ptit.snapvocab.notification.exception;

import vn.ptit.snapvocab.notification.domain.enumeration.NotificationErrorCode;

public class NotificationException extends RuntimeException {

	private final NotificationErrorCode code;

	public NotificationException(NotificationErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public NotificationErrorCode getCode() {
		return code;
	}
}
