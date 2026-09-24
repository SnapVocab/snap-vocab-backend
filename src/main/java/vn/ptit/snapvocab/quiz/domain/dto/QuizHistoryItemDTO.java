package vn.ptit.snapvocab.quiz.domain.dto;

import java.time.Instant;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;

public record QuizHistoryItemDTO(Long quizId, Long topicId, String topicName, QuizMode mode,
		QuizDirection direction, QuizStatus status, int questionCount, Integer correctCount,
		Integer incorrectCount, Double accuracy, Instant createdAt, Instant completedAt) {
}
