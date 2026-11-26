package vn.ptit.snapvocab.dictionary.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Bodies of /api/admin/words (snapAdmin domains/admin-content/types.ts). */
public final class AdminWordDTOs {

	private AdminWordDTOs() {
	}

	/**
	 * The whole editable part of a word: every IPA, the Vietnamese translations and every meaning. Translations in
	 * other languages are kept. A meaning with {@code id} (word_definitions.id from the lookup) is kept and updated;
	 * one without is new; a meaning the body leaves out is removed from this word.
	 */
	public record UpdateWordRequest(
			@NotNull Long version,
			@NotNull @Size(max = 20) List<@Valid @NotNull PronunciationInput> pronunciations,
			@NotNull @Size(max = 50) List<@NotBlank @Size(max = 1024) String> translations,
			@NotNull @Size(max = 100) List<@Valid @NotNull MeaningInput> meanings) {
	}

	public record PronunciationInput(@NotBlank @Size(max = 512) String ipa, @Size(max = 100) String region) {
	}

	/** {@code pos} is the stored code (N, V, A…, PosLabel), not the label the lookup shows. */
	public record MeaningInput(
			Long id,
			@Size(max = 50) String pos,
			@NotNull @Pattern(regexp = "vi|en") String definitionLang,
			@NotBlank @Size(max = 4000) String definition,
			@Size(max = 4000) String example) {
	}

	public record DeletedWordDTO(Long id, String word, Instant deletedAt) {
	}
}
