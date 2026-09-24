package vn.ptit.snapvocab.quiz.domain.dto;

import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;

public record QuizCompletedEvent(Long quizId, Long userId, Long topicId, QuizMode mode, int totalUnits,
		int correctCount) {
}
