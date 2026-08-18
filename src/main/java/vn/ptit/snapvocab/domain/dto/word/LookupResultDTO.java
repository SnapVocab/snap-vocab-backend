package vn.ptit.snapvocab.domain.dto.word;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LookupResultDTO {

    private boolean exists;

    private String word;

    private String langCode;

    private String langName;

    private List<PronunciationLookupDTO> pronunciations;

    private List<MeaningDTO> meanings;

    private List<TranslationLookupDTO> translations;

    private List<RelationLookupDTO> relations;
}
