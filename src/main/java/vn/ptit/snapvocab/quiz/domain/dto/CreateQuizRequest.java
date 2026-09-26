package vn.ptit.snapvocab.quiz.domain.dto;

import jakarta.validation.constraints.NotNull;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;

public record CreateQuizRequest(
		@NotNull Long topicId,
		@NotNull QuizMode mode,
		QuizDirection direction,
		@NotNull Integer requestedCount) {
}

