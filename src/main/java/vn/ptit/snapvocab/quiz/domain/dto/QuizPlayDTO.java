package vn.ptit.snapvocab.quiz.domain.dto;

import java.util.List;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;

public record QuizPlayDTO(
		Long quizId,
		QuizStatus status,
		QuizMode mode,
		QuizDirection direction,
		Long topicId,
		String topicName,
		int requestedCount,
		List<Question> questions,
		List<MatchingRound> matchingRounds) {

	public record Question(Long questionId, int position, String prompt, List<Option> options,
			QuizOutcome outcome, boolean resolved) {
	}

	public record Option(String key, String label, int displayOrder) {
	}

	public record MatchingRound(int roundNo, int totalPairs, int matchedCount, boolean completed,
			List<LeftTile> leftTiles, List<RightTile> rightTiles) {
	}

	public record LeftTile(String key, String label, int displayOrder, Long questionId, QuizOutcome outcome,
			boolean resolved, int attemptCount) {
	}

	public record RightTile(String key, String label, int displayOrder, boolean matched) {
	}
}
