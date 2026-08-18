package vn.ptit.snapvocab.domain.dto.word;

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
public class WordDTO {

	private Long id;

	@NotNull
	@Size(min = 1, max = 255)
	private String word;

	@NotNull
	@Size(min = 2, max = 20)
	private String langCode;
}
