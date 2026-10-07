package vn.ptit.snapvocab.mission.exception;

import org.springframework.http.HttpStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionErrorCode;

public class MissionException extends RuntimeException {

	private final MissionErrorCode code;
	private final HttpStatus status;
	private final Object details;

	public MissionException(MissionErrorCode code, String message) {
		this(code, code.getStatus(), message, null);
	}

	/** {@code status} overrides the code's default, e.g. an Admin write breaking the catalog is 409, not 503. */
	public MissionException(MissionErrorCode code, HttpStatus status, String message, Object details) {
		super(message);
		this.code = code;
		this.status = status;
		this.details = details;
	}

	public MissionErrorCode getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public Object getDetails() {
		return details;
	}
}
