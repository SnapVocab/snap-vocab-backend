package vn.ptit.snapvocab.quiz.domain.dto;

import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;

/** {@code meaning} is set for LISTENING only: the heard word's meaning, revealed after answering. */
public record QuestionCheckDTO(Long questionId, QuizOutcome outcome, boolean correct, String correctAnswer,
		String correctOptionKey, String meaning) {
}
