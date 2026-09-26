package vn.ptit.snapvocab.quiz.domain.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;

public record QuizResultDTO(
		Long quizId,
		QuizStatus status,
		QuizMode mode,
		QuizDirection direction,
		Long topicId,
		String topicName,
		Summary summary,
		Instant completedAt,
		Instant cancelledAt,
		Long durationSeconds,
		List<Item> items) {

	public record Summary(int totalUnits, int correctCount, int incorrectCount, double accuracy) {
	}

	public record Item(Long questionId, int position, Integer roundNo, String prompt, String learnerAnswer,
			String correctAnswer, QuizOutcome outcome, boolean resolved, int attemptCount) {
	}
}

