package vn.ptit.snapvocab.dictionary.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TranslationLookupDTO {

    private String langCode;
    private String translation;
}

