package vn.ptit.snapvocab.study.domain.dto;

import java.time.LocalDateTime;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

/**
 * {@code xpAwarded} includes any booster bonus and is 0 when the card was already reviewed today.
 * {@code becameMastered} is true only on the review that first lifts the card to Mastered.
 */
public record ReviewResultDTO(
		Long topicItemId,
		CardState state,
		LocalDateTime due,
		Float stability,
		Float difficulty,
		Integer reps,
		Integer lapses,
		int xpAwarded,
		boolean becameMastered
) {
}
