package vn.ptit.snapvocab.vocab.domain.dto.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.vocab.domain.enumeration.Alignment;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.DataType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateTemplateDTO {

	@NotBlank(message = "Template name is required")
	@Size(min = 1, max = 100, message = "Template name must be between 1 and 100 characters")
	private String name;

	private Boolean isDefault;

	@NotEmpty(message = "elements must not be empty")
	@Valid
	private List<CreateTemplateElementDTO> elements;

	@Getter
	@Setter
	@AllArgsConstructor
	@NoArgsConstructor
	@Builder
	public static class CreateTemplateElementDTO {

		private Integer position;

		@NotNull(message = "Element type is required")
		private TemplateElementType type;

		@NotNull(message = "Card side is required")
		private CardSide side;

		@Size(max = 100)
		private String sectionLabel;

		private Boolean repeatable;

		@Valid
		private CreateTemplateFieldDTO field;
	}

	@Getter
	@Setter
	@AllArgsConstructor
	@NoArgsConstructor
	@Builder
	public static class CreateTemplateFieldDTO {

		private Long schemaAttributeId;
		private String attributeName;
		private DataType dataType;
		private Boolean required;
		private SemanticRole semanticRole;
		private String fieldLabel;
		private boolean hideIfEmpty;
		private boolean audioAction;
		private Integer fontSize;
		private Alignment alignment;
		private String color;
	}
}

