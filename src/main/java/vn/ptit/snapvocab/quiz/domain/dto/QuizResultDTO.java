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
		Xp xp,
		List<Item> items) {

	/** XP actually written for this quiz ({@code null} until COMPLETED with ≥1 correct); bonus &gt; 0 when a booster applied. */
	public record Xp(int baseAmount, int bonusAmount, int amount) {
	}

	public record Summary(int totalUnits, int correctCount, int incorrectCount, double accuracy) {
	}

	public record Item(Long questionId, int position, Integer roundNo, String prompt, String learnerAnswer,
			String correctAnswer, QuizOutcome outcome, boolean resolved, int attemptCount) {
	}
}
