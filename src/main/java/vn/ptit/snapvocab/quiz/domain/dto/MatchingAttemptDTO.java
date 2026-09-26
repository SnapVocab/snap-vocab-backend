package vn.ptit.snapvocab.quiz.domain.dto;

import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;

public record MatchingAttemptDTO(Long quizId, int roundNo, String leftTileKey, String rightTileKey,
		boolean correct, int attemptNo, QuizOutcome outcome, boolean resolved, Round round) {

	public record Round(int matchedCount, int totalPairs, boolean completed) {
	}
}

