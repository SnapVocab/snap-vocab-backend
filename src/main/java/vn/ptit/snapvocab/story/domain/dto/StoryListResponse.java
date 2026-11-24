package vn.ptit.snapvocab.story.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryListResponse {

	private List<StoryDTO> items;
	private int page;
	private int size;
	private long totalElements;
	private int totalPages;
	private QuotaDTO quota;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class QuotaDTO {
		private int usedToday;
		private int dailyLimit;
		private int remaining;
		private int minWords;
		private int maxWords;
	}
}

