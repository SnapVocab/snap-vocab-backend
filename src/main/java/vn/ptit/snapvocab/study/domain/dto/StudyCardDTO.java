package vn.ptit.snapvocab.study.domain.dto;

import java.time.LocalDateTime;
import vn.ptit.snapvocab.mnemonic.dto.MnemonicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

/**
 * {@code collectionName} is the source tag shown on a card when one session mixes collections.
 * {@code forgetCount} counts every "Quên" so far; {@code mnemonic} is the learner's own AI mnemonic, or null.
 */
public record StudyCardDTO(
		TopicItemData item,
		Long topicId,
		String collectionName,
		CardState state,
		LocalDateTime due,
		int forgetCount,
		MnemonicDTO mnemonic
) {
}
