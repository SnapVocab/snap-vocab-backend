package vn.ptit.snapvocab.mission.domain.enumeration;

import org.springframework.http.HttpStatus;

/** daily_mission.md §8. A missing/invalid Idempotency-Key reuses the Shop code {@code IDEMPOTENCY_KEY_REQUIRED}. */
public enum MissionErrorCode {
	MISSION_POOL_INSUFFICIENT(HttpStatus.SERVICE_UNAVAILABLE),
	MISSION_NOT_FOUND(HttpStatus.NOT_FOUND),
	MISSION_NOT_COMPLETED(HttpStatus.CONFLICT),
	MISSION_EXPIRED(HttpStatus.CONFLICT),
	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
	// Admin mission templates (daily_mission.md §8.3). There MISSION_POOL_INSUFFICIENT is a 409, see MissionException.
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	REWARD_ITEM_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST),
	MISSION_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND),
	MISSION_TEMPLATE_CODE_EXISTS(HttpStatus.CONFLICT),
	FIELD_LOCKED(HttpStatus.CONFLICT),
	VERSION_CONFLICT(HttpStatus.CONFLICT);

	private final HttpStatus status;

	MissionErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
