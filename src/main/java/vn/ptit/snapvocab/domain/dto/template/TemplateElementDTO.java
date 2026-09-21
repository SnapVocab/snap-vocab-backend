package vn.ptit.snapvocab.domain.dto.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.domain.enumeration.TemplateElementType;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateElementDTO {

	private Long id;
	private Integer position;
	private TemplateElementType type;
	private CardSide side;
	private String sectionLabel;
	private Boolean repeatable;
	private TemplateFieldDTO field;
}
