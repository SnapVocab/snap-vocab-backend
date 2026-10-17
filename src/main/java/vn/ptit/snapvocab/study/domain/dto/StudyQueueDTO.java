package vn.ptit.snapvocab.study.domain.dto;

import java.util.List;

public record StudyQueueDTO(
		int limit,
		int dailyGoal,
		int studiedToday,
		int remainingToday,
		List<StudyCardDTO> cards
) {
}
