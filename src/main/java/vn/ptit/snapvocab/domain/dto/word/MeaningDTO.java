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
public class MeaningDTO {

    private String definition;

    private String definitionLang;

    private String example;

    private String pos;

    private String subPos;

    private List<String> links;
}
