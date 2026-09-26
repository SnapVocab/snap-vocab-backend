package vn.ptit.snapvocab.vocab.domain.dto.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.vocab.domain.enumeration.Alignment;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateFieldDTO {

	private Long id;
	private Long schemaAttributeId;
	private String attributeName;
	private SemanticRole semanticRole;
	private String fieldLabel;
	private boolean hideIfEmpty;
	private boolean audioAction;
	private Integer fontSize;
	private Alignment alignment;
	private String color;
}

