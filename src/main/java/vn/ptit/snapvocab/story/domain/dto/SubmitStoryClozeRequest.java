package vn.ptit.snapvocab.story.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class SubmitStoryClozeRequest {

	@NotEmpty(message = "answers must not be empty")
	@Valid
	private List<BlankAnswer> answers;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BlankAnswer {
		private int blankIndex;
		private String value;
	}
}

