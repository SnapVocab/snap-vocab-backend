package vn.ptit.snapvocab.vocab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.ptit.snapvocab.vocab.config.SemanticBindingMigration.ForkMode;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateFieldDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.DataType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.vocab.domain.mapper.TemplateMapper;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;

class PhoneticBindingMigrationTest {

	@Test
	void upgradesExistingVisibleFieldAndExposesRoleIdempotently() {
		var schemas = mock(SchemaRepository.class);
		var groups = mock(SchemaAttributeGroupRepository.class);
		var templates = mock(TemplateRepository.class);
		var schema = Schema.builder().id(1L).code("DEFAULT_ENGLISH").build();
		var translation = attribute(2L, "translation");
		var phonetic = attribute(3L, "phonetic");
		var content = attribute(4L, "content");
		var main = SchemaAttributeGroup.builder().name("main")
				.attributes(new ArrayList<>(List.of(translation, phonetic))).build();
		var examples = SchemaAttributeGroup.builder().name("examples")
				.attributes(new ArrayList<>(List.of(content))).build();
		var template = Template.builder().id(1L).code("STANDARD").build();
		var oldField = field(phonetic, null);
		oldField.setFieldLabel("IPA");
		oldField.setFontSize(18);
		template.addElement(oldField.getElement());
		template.addElement(field(translation, SemanticRole.NATIVE_TRANSLATION).getElement());
		template.addElement(field(content, SemanticRole.EXAMPLE_SENTENCE).getElement());
		when(schemas.findByCode("DEFAULT_ENGLISH")).thenReturn(Optional.of(schema));
		when(schemas.findByIsSystemFalse()).thenReturn(List.of());
		when(groups.findBySchemaIdWithAttributes(1L)).thenReturn(List.of(main, examples));
		when(templates.findBySchemaId(1L)).thenReturn(List.of(template));
		var migration = new SemanticBindingMigration(schemas, groups, templates, ForkMode.REPORT);

		assertThat(migration.migrate().get(0).bindingsAdded()).isEqualTo(1);
		assertThat(oldField.getSemanticRole()).isEqualTo(SemanticRole.PHONETIC);
		assertThat(oldField.isHidden()).isFalse();
		assertThat(oldField.getFieldLabel()).isEqualTo("IPA");
		assertThat(oldField.getFontSize()).isEqualTo(18);
		assertThat(template.getElements()).hasSize(3);
		assertThat(Mappers.getMapper(TemplateMapper.class).toDto(template).getElements())
				.anySatisfy(e -> assertThat(e.getField().getSemanticRole()).isEqualTo(SemanticRole.PHONETIC));
		assertThat(migration.migrate().get(0).bindingsAdded()).isZero();

		// Explicit user roles must never be overwritten by a name-based migration.
		oldField.setSemanticRole(SemanticRole.DEFINITION);
		assertThat(migration.migrate().get(0).bindingsAdded()).isZero();
		assertThat(oldField.getSemanticRole()).isEqualTo(SemanticRole.DEFINITION);
	}

	@Test
	void templateApiAcceptsAndReturnsPhoneticRole() throws Exception {
		var mapper = new ObjectMapper();
		var dto = mapper.readValue("{\"semanticRole\":\"PHONETIC\"}", TemplateFieldDTO.class);
		assertThat(dto.getSemanticRole()).isEqualTo(SemanticRole.PHONETIC);
		assertThat(mapper.readTree(mapper.writeValueAsString(dto)).get("semanticRole").asText())
				.isEqualTo("PHONETIC");
	}

	private static SchemaAttribute attribute(long id, String name) {
		return SchemaAttribute.builder().id(id).name(name).dataType(DataType.TEXT).build();
	}

	private static TemplateField field(SchemaAttribute attribute, SemanticRole role) {
		var element = TemplateElement.builder().type(TemplateElementType.FIELD).position(0).build();
		var field = TemplateField.builder().element(element).schemaAttribute(attribute).semanticRole(role).build();
		element.setField(field);
		return field;
	}
}
