package vn.ptit.snapvocab.domain.dto.note;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
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
public class NoteDTO {

	private Long id;

	@NotNull
	private Long deckId;

	@NotBlank
	@Size(max = 255)
	private String word;

	@Valid
	@NotNull
	@Builder.Default
	private List<NotePronunciationDTO> notePronunciations = new ArrayList<>();

	@Valid
	@NotNull
	@Builder.Default
	private List<NoteMeaningDTO> noteMeanings = new ArrayList<>();
}
