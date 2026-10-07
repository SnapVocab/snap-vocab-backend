package vn.ptit.snapvocab.quiz.domain.dto;

import java.time.Instant;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;

/** Published synchronously inside the completing transaction; {@code completedAt} is its single actionInstant. */
public record QuizCompletedEvent(Long quizId, Long userId, Long topicId, QuizMode mode, int totalUnits,
		int correctCount, Instant completedAt) {
}
