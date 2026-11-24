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
public class SubmitStoryClozeResponse {

	private Long storyId;
	private int correctCount;
	private int totalBlanks;
	private int accuracyPercent;
	private int xpAwardedNow;
	private int totalXpAwarded;
	private int bestScore;
	private List<BlankResult> results;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BlankResult {
		private int blankIndex;
		private String submitted;
		private String expected;
		private boolean correct;
	}
}

