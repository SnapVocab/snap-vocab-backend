package vn.ptit.snapvocab.dictionary.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PronunciationLookupDTO {

    private String ipa;
    private String region;
}

