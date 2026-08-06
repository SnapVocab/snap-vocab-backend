package vn.ptit.snapvocab.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Dictionary data returned once per unique scan label. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScanWordDTO(
		boolean exists,
		String word,
		String langCode,
		String langName,
		List<PronunciationLookupDTO> pronunciations,
		List<MeaningDTO> meanings,
		List<TranslationLookupDTO> translations,
		List<RelationLookupDTO> relations
) {

	public static ScanWordDTO from(String normalizedLabel, LookupResultDTO result) {
		return new ScanWordDTO(
				result.isExists(),
				normalizedLabel,
				result.getLangCode(),
				result.getLangName(),
				result.getPronunciations(),
				result.getMeanings(),
				result.getTranslations(),
				result.getRelations());
	}

	public static ScanWordDTO missing(String label) {
		return new ScanWordDTO(false, label, null, null, null, null, null, null);
	}
}
