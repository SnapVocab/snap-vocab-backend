package vn.ptit.snapvocab.notification.domain.enumeration;

import org.springframework.http.HttpStatus;

/** notification.md §4.2. Bean-validation failures stay the shared {@code Validation failed}. */
public enum NotificationErrorCode {
	INVALID_DEVICE_TOKEN(HttpStatus.BAD_REQUEST),
	ROUTE_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
	/** Unknown id or someone else's: never tell which. */
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
	DUPLICATE_NOTIFICATION(HttpStatus.CONFLICT);

	private final HttpStatus status;

	NotificationErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
