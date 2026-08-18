package vn.ptit.snapvocab.domain.dto.note;

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
public class NoteMeaningDTO {

	private Long id;

	private String meaning;

	private String partOfSpeech;

	private String example;

	private String personalNote;
}
