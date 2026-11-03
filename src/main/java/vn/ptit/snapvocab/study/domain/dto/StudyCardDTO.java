package vn.ptit.snapvocab.study.domain.dto;

import java.time.LocalDateTime;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

/** {@code collectionName} is the source tag shown on a card when one session mixes collections. */
public record StudyCardDTO(
		TopicItemData item,
		Long topicId,
		String collectionName,
		CardState state,
		LocalDateTime due
) {
}
