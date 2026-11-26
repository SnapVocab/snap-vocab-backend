package vn.ptit.snapvocab.story.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryTopicSummaryDTO {

	private Long id;
	private String name;
	private String translation;
	private Long collectionId;
	private String collectionName;
	private Long parentId;
	private String parentName;
	private int wordCount;
}

