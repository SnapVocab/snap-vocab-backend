package vn.ptit.snapvocab.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WordRelationDTO {

	private Long id;

	@Size(max = 255)
	private String relatedWord;

	@Size(max = 50)
	private String relationType;

	@NotNull
	private WordDTO word;
}
