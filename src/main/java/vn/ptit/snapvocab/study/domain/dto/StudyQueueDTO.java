package vn.ptit.snapvocab.study.domain.dto;

import java.util.List;

/** One session: due cards first, then the new words today still allows (learn_ui.md §4.1). */
public record StudyQueueDTO(
		int limit,
		List<StudyCardDTO> cards
) {
}
