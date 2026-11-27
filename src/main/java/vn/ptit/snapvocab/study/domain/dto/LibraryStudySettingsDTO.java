package vn.ptit.snapvocab.study.domain.dto;

import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;

/**
 * One row of Settings → "Giới hạn học theo bộ": each collection of the learner's library with its daily limits
 * ({@code customized} = false means the defaults 20 new / 200 review).
 */
public record LibraryStudySettingsDTO(
		Long collectionId,
		String name,
		String translation,
		CollectionType type,
		int newCardsPerDay,
		int reviewCardsPerDay,
		boolean customized
) {
}
