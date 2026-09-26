package vn.ptit.snapvocab.dictionary.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RelationLookupDTO {

    private String relatedWord;
    private String relationType;
}

