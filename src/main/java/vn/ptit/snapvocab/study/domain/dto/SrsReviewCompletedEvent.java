package vn.ptit.snapvocab.study.domain.dto;

import io.github.openspacedrepetition.Rating;
import java.time.Instant;

/** One persisted rating, published synchronously in the review transaction. */
public record SrsReviewCompletedEvent(Long userId, Long collectionId, Long topicItemId,
		int reviewNumber, Rating rating, Instant reviewedAt, String idempotencyKey) {

	/**
	 * With an Idempotency-Key the client's key identifies the rating, so a retried POST finds it and replays.
	 * Without one (older apps), the card's locked, increasing reps identifies each accepted rating.
	 */
	public String sourceKey() {
		return idempotencyKey != null ? keyedSourceKey(userId, idempotencyKey)
				: "SRS_REVIEW_COMPLETED:" + userId + ":" + topicItemId + ":" + reviewNumber;
	}

	public static String keyedSourceKey(Long userId, String idempotencyKey) {
		return "SRS_REVIEW_COMPLETED:" + userId + ":KEY:" + idempotencyKey;
	}
}
