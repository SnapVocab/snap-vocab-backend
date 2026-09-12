package vn.ptit.snapvocab.domain.dto.word;

import jakarta.validation.constraints.NotNull;
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
public class TranslationDTO {

	private Long id;

	@Size(max = 20)
	private String langCode;

	@Size(max = 1024)
	private String translation;

	@NotNull
	private WordDTO word;

	private Instant createdAt;
}
