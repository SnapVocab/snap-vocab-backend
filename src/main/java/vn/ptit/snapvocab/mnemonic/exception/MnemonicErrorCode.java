package vn.ptit.snapvocab.mnemonic.exception;

import org.springframework.http.HttpStatus;

public enum MnemonicErrorCode {
	/** No review record of this word for the learner. */
	ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
	/** Forgotten fewer than {@code MnemonicService.MIN_FORGET_COUNT} times. */
	NOT_A_LEECH(HttpStatus.BAD_REQUEST),
	/** The card has no word to build a mnemonic on. */
	WORD_MISSING(HttpStatus.BAD_REQUEST),
	/** Daily generations used up; details carry the limit. */
	MNEMONIC_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
	/** Every configured AI provider failed or returned an unusable answer; nothing was charged. */
	AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

	private final HttpStatus status;

	MnemonicErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
