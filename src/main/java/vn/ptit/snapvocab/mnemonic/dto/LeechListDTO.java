package vn.ptit.snapvocab.mnemonic.dto;

import java.util.List;

/** {@code total}: every leech of the learner; {@code items} holds the first {@code limit} of them. */
public record LeechListDTO(List<LeechWordDTO> items, long total, int minForgetCount, int dailyLimit,
		int remainingToday) {
}
