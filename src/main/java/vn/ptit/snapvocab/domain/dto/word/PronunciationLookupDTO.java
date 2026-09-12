package vn.ptit.snapvocab.domain.dto.word;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PronunciationLookupDTO {

    private String ipa;
    private String region;
}
