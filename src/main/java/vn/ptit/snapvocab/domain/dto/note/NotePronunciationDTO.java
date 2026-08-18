package vn.ptit.snapvocab.domain.dto.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class NotePronunciationDTO {

	private Long id;

	@NotBlank
	@Size(max = 255)
	private String ipa;
}
