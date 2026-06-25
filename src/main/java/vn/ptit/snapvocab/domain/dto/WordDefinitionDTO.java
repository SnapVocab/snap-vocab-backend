package vn.ptit.snapvocab.domain.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;
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
public class WordDefinitionDTO {

	private Long id;

	@Lob
	private String example;

	@NotNull
	private WordDTO word;

	@NotNull
	private DefinitionDTO definition;
}
