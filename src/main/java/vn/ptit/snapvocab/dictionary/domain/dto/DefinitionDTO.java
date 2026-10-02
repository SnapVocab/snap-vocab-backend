package vn.ptit.snapvocab.dictionary.domain.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
public class DefinitionDTO {

	private Long id;

	@Lob
	private String definition;

	@Size(max = 50)
	private String pos;

	@Size(max = 50)
	private String subPos;

	@Size(max = 10)
	private String definitionLang;

	@Size(max = 2048)
	private String links;

	private Instant createdAt;
}

