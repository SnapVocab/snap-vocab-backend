package vn.ptit.snapvocab.story.domain.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.story.domain.enumeration.StoryGenre;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryDTO {

	private Long id;
	private Long topicId;
	private String title;
	private StoryGenre genre;
	private String providerName;
	private String modelName;
	private List<TargetWordDTO> targetWords;
	private List<StorySentenceDTO> sentences;
	private int totalBlanks;
	private Integer bestScore;
	private int xpAwarded;
	private Instant completedAt;
	private Instant createdAt;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TargetWordDTO {
		private String word;
		private String meaning;
		private String phonetic;
		private String partOfSpeech;
	}

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class StorySentenceDTO {
		private int index;
		private int paragraph;
		private String text;
		private List<StoryBlankDTO> blanks;
	}

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class StoryBlankDTO {
		private int blankIndex;
		private String answer;
		private String displayWord;
	}
}
