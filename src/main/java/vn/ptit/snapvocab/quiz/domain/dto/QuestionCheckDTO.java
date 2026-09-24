package vn.ptit.snapvocab.quiz.domain.dto;

import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;

public record QuestionCheckDTO(Long questionId, QuizOutcome outcome, boolean correct, String correctAnswer,
		String correctOptionKey) {
}
