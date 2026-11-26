package vn.ptit.snapvocab.story.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryTopicWordsResponse {

	private Long topicId;
	private String topicName;
	private String topicTranslation;
	private List<TopicWordItem> words;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TopicWordItem {
		private Long itemId;
		private String word;
		private String translation;
	}
}

