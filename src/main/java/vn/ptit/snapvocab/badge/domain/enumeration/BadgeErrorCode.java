package vn.ptit.snapvocab.badge.domain.enumeration;

import org.springframework.http.HttpStatus;

/** Admin PATCH errors (badges.md §8.4). Unknown id stays ResourceNotFoundException("BADGE_NOT_FOUND"), as in P1. */
public enum BadgeErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	/** rewardItemCode is not a PUBLISHED, non-purchasable AVATAR_FRAME or CARD_FRAME (PD-BDG-14). */
	REWARD_ITEM_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST),
	FIELD_LOCKED(HttpStatus.CONFLICT),
	VERSION_CONFLICT(HttpStatus.CONFLICT);

	private final HttpStatus status;

	BadgeErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
