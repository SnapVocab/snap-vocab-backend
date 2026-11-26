package vn.ptit.snapvocab.vocab.exception;

import java.util.Map;

public class ContentException extends RuntimeException {

	private final ContentErrorCode code;
	private final transient Map<String, Object> details;

	public ContentException(ContentErrorCode code, String message, Map<String, Object> details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public ContentException(ContentErrorCode code, String message) {
		this(code, message, null);
	}

	public static ContentException invalid(String field, String message) {
		return new ContentException(ContentErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
	}

	public static ContentException versionConflict(String what, long currentVersion) {
		return new ContentException(ContentErrorCode.VERSION_CONFLICT,
				what + " was modified by someone else; reload it", Map.of("currentVersion", currentVersion));
	}

	public ContentErrorCode getCode() {
		return code;
	}

	public Map<String, Object> getDetails() {
		return details;
	}
}
