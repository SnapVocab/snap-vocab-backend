package vn.ptit.snapvocab.progress.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.openspacedrepetition.Rating;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.study.domain.dto.SrsReviewCompletedEvent;

class ProgressEventListenerTest {
	@Test
	void acceptedSrsRatingUsesTheReviewTimestampAndStableActionKey() {
		ProgressService progress = mock(ProgressService.class);
		SrsReviewCompletedEvent event = new SrsReviewCompletedEvent(7L, 3L, 21L, 4, Rating.AGAIN,
				Instant.parse("2026-10-31T17:30:00Z"), null);
		new ProgressEventListener(progress).onSrsReviewCompleted(event);
		verify(progress).record(eq(7L), eq(LearningEventType.SRS_REVIEW_COMPLETED),
				eq("SRS_REVIEW_COMPLETED:7:21:4"),
				eq(Map.of("collectionId", 3L, "topicItemId", 21L, "reviewNumber", 4, "rating", "AGAIN")),
				eq(event.reviewedAt()));
	}

	@Test
	void withAnIdempotencyKeyTheClientKeyIdentifiesTheRating() {
		SrsReviewCompletedEvent event = new SrsReviewCompletedEvent(7L, 3L, 21L, 4, Rating.GOOD, Instant.EPOCH, "k1");
		org.assertj.core.api.Assertions.assertThat(event.sourceKey()).isEqualTo("SRS_REVIEW_COMPLETED:7:KEY:k1")
				.isEqualTo(SrsReviewCompletedEvent.keyedSourceKey(7L, "k1"));
	}
}
