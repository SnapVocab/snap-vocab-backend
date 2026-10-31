package vn.ptit.snapvocab.study.domain.dto;

import io.github.openspacedrepetition.Rating;
import java.time.Instant;

/** One persisted rating, published synchronously in the review transaction. */
public record SrsReviewCompletedEvent(Long userId, Long collectionId, Long topicItemId,
		int reviewNumber, Rating rating, Instant reviewedAt) {

	/** A card can be reviewed repeatedly; its locked, increasing reps identifies each accepted rating. */
	public String sourceKey() {
		return "SRS_REVIEW_COMPLETED:" + userId + ":" + topicItemId + ":" + reviewNumber;
	}
}
