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
public class PronunciationDTO {

	private Long id;

	@Size(max = 512)
	private String ipa;

	@Size(max = 100)
	private String region;

	@NotNull
	private WordDTO word;
}
