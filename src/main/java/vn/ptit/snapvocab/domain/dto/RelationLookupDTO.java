package vn.ptit.snapvocab.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RelationLookupDTO {

    private String relatedWord;
    private String relationType;
}
