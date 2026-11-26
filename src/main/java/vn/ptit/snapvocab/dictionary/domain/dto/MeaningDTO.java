package vn.ptit.snapvocab.dictionary.domain.dto;

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
public class MeaningDTO {
    /** word_definitions.id: lets the admin editor keep a meaning it did not change. */
    private Long id;

    private String definition;

    private String definitionLang;

    private String example;

    private String pos;

    private String subPos;

    private List<String> links;
}

