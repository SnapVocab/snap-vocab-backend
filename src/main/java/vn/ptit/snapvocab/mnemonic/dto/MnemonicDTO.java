package vn.ptit.snapvocab.mnemonic.dto;

import java.time.Instant;
import vn.ptit.snapvocab.mnemonic.domain.MnemonicType;
import vn.ptit.snapvocab.mnemonic.domain.WordMnemonic;

public record MnemonicDTO(Long topicItemId, MnemonicType type, String keyword, String content, Instant updatedAt) {

	public static MnemonicDTO of(WordMnemonic mnemonic) {
		return new MnemonicDTO(mnemonic.getTopicItem().getId(), mnemonic.getType(), mnemonic.getKeyword(),
				mnemonic.getContent(), mnemonic.getUpdatedAt());
	}
}
