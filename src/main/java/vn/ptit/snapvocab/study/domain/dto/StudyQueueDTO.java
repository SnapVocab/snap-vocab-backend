package vn.ptit.snapvocab.study.domain.dto;

import java.util.List;

public record StudyQueueDTO(
		int limit,
		List<StudyCardDTO> cards
) {
}
