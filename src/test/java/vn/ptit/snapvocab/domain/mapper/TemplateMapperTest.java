package vn.ptit.snapvocab.domain.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.Template;
import vn.ptit.snapvocab.domain.TemplateElement;
import vn.ptit.snapvocab.domain.TemplateField;
import vn.ptit.snapvocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.domain.dto.template.TemplateElementDTO;
import vn.ptit.snapvocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.domain.enumeration.TemplateElementType;

class TemplateMapperTest {

	private final TemplateMapper mapper = Mappers.getMapper(TemplateMapper.class);

	private static TemplateElement fieldElement(long id, int position, SemanticRole role, boolean hidden) {
		TemplateElement element = TemplateElement.builder().id(id).position(position).side(CardSide.BACK)
				.type(TemplateElementType.FIELD).build();
		element.setField(TemplateField.builder().id(id).element(element)
				.schemaAttribute(SchemaAttribute.builder().id(id).name("a" + id).build())
				.semanticRole(role).hidden(hidden).build());
		return element;
	}

	@Test
	void hiddenFieldElementIsRemovedEntirelyFromClientDto() {
		Template template = Template.builder().id(1L).code("STANDARD").build();
		template.addElement(TemplateElement.builder().id(10L).position(0).side(CardSide.FRONT)
				.type(TemplateElementType.SECTION_BREAK).build());
		template.addElement(fieldElement(11L, 1, SemanticRole.TARGET_WORD, false));
		template.addElement(fieldElement(12L, 2, SemanticRole.NATIVE_TRANSLATION, true));
		template.addElement(fieldElement(13L, 3, SemanticRole.EXAMPLE_SENTENCE, true));

		TemplateDTO dto = mapper.toDto(template);

		assertThat(dto.getElements()).extracting(TemplateElementDTO::getId).containsExactly(10L, 11L);
		assertThat(dto.getElements()).noneMatch(e -> e.getType() == TemplateElementType.FIELD && e.getField() == null);
		assertThat(dto.getElements().get(1).getField().getSemanticRole()).isEqualTo(SemanticRole.TARGET_WORD);
	}

	@Test
	void templateWithoutHiddenFieldsMapsUnchanged() {
		Template template = Template.builder().id(1L).code("REVERSE").build();
		template.addElement(fieldElement(11L, 0, SemanticRole.DEFINITION, false));
		template.addElement(fieldElement(12L, 1, null, false));

		assertThat(mapper.toDto(template).getElements()).extracting(TemplateElementDTO::getId)
				.containsExactly(11L, 12L);
	}
}
