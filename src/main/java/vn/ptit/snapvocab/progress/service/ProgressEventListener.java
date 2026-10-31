package vn.ptit.snapvocab.progress.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.quiz.domain.dto.QuizCompletedEvent;
import vn.ptit.snapvocab.study.domain.dto.SrsReviewCompletedEvent;

/** Synchronous: runs inside the producer's transaction, so the learning event commits or rolls back with it. */
@Component
@RequiredArgsConstructor
public class ProgressEventListener {

	private final ProgressService progressService;

	@EventListener
	public void onQuizCompleted(QuizCompletedEvent event) {
		progressService.record(event.userId(), LearningEventType.QUIZ_COMPLETED,
				"QUIZ_COMPLETED:" + event.quizId(),
				Map.of("quizId", event.quizId(), "topicId", event.topicId(), "mode", event.mode().name(),
						"totalUnits", event.totalUnits(), "correctCount", event.correctCount()),
				event.completedAt());
	}

	@EventListener
	public void onSrsReviewCompleted(SrsReviewCompletedEvent event) {
		progressService.record(event.userId(), LearningEventType.SRS_REVIEW_COMPLETED, event.sourceKey(),
				Map.of("collectionId", event.collectionId(), "topicItemId", event.topicItemId(),
						"reviewNumber", event.reviewNumber(), "rating", event.rating().name()),
				event.reviewedAt());
	}
}
