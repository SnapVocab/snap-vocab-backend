package vn.ptit.snapvocab.study.domain.dto;

import java.time.LocalDateTime;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;

/** A row of "Bộ từ của bạn": own collections plus system collections the learner has started. */
public record StudyCollectionDTO(
		Long id,
		String name,
		String translation,
		CollectionType type,
		long wordCount,
		long dueCount,
        long newCount,
		LocalDateTime lastStudiedAt
) {
}
