package vn.ptit.snapvocab.study.domain.dto;

import java.time.LocalDateTime;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

public record StudyCardDTO(
		TopicItemData item,
		Long topicId,
		CardState state,
		LocalDateTime due
) {
}
