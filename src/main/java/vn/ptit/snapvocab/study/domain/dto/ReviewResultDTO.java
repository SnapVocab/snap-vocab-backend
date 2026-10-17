package vn.ptit.snapvocab.study.domain.dto;

import java.time.LocalDateTime;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

public record ReviewResultDTO(
		Long topicItemId,
		CardState state,
		LocalDateTime due,
		Float stability,
		Float difficulty,
		Integer reps,
		Integer lapses
) {
}
