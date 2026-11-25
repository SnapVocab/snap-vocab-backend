package vn.ptit.snapvocab.story.domain.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.story.domain.enumeration.StoryGenre;
import vn.ptit.snapvocab.story.domain.enumeration.StoryLevel;
import vn.ptit.snapvocab.story.domain.enumeration.StoryMode;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateStoryRequest {

	/** TOPIC | SCAN | MY_WORDS | CUSTOM */
	private String source;

	private Long topicId;

	/** Optional auto-pick count when resolving words from a Topic (defaults to 7, max 20). */
	private Integer wordCount;

	@Size(max = 20, message = "Maximum 20 words allowed per story")
	private List<String> words;

	private StoryMode mode;

	private StoryLevel level;

	private StoryGenre genre;
}

