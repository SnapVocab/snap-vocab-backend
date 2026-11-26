package vn.ptit.snapvocab.vocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.dto.template.CreateTemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.MyTemplateDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.vocab.domain.mapper.TemplateMapper;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

/** "Mẫu thẻ của tôi": one personal copy per base schema, update in place, delete only when unused. */
@ExtendWith(MockitoExtension.class)
class SchemaServiceTemplateLibraryTest {

	private static final Long USER_ID = 4L;

	@Mock
	private SchemaRepository schemaRepository;
	@Mock
	private TopicRepository topicRepository;
	@Mock
	private SchemaAttributeGroupRepository schemaAttributeGroupRepository;
	@Mock
	private TemplateRepository templateRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private TemplateMapper templateMapper;

	@InjectMocks
	private SchemaService schemaService;

	private User learner;
	private Schema systemSchema;
	private SchemaAttribute systemWord;

	@BeforeEach
	void setUp() {
		learner = User.builder().id(USER_ID).email("l@test.local").firstName("L").password("x").build();
		systemSchema = Schema.builder().id(1L).code("DEFAULT_ENGLISH").name("Default English").isSystem(true).build();
		SchemaAttributeGroup group = SchemaAttributeGroup.builder().id(10L).name("main").schema(systemSchema).build();
		systemWord = SchemaAttribute.builder().id(100L).name("word").label("Word").group(group).build();
		group.setAttributes(new ArrayList<>(List.of(systemWord)));
		systemSchema.getAttributeGroups().add(group);
	}

	private static CreateTemplateDTO request(Long attributeId) {
		return CreateTemplateDTO.builder()
				.name("Mẫu của tôi")
				.elements(List.of(CreateTemplateDTO.CreateTemplateElementDTO.builder()
						.type(TemplateElementType.FIELD)
						.side(CardSide.FRONT)
						.field(CreateTemplateDTO.CreateTemplateFieldDTO.builder()
								.schemaAttributeId(attributeId)
								.semanticRole(SemanticRole.TARGET_WORD)
								.build())
						.build()))
				.build();
	}

	private Schema personalCopy() {
		Schema copy = Schema.builder().id(2L).code(SchemaService.personalCopyCode(USER_ID, 1L)).name("Default English")
				.isSystem(false).user(learner).build();
		SchemaAttributeGroup group = SchemaAttributeGroup.builder().id(20L).name("main").schema(copy).build();
		SchemaAttribute word = SchemaAttribute.builder().id(200L).name("word").label("Word").group(group).build();
		group.setAttributes(new ArrayList<>(List.of(word)));
		copy.getAttributeGroups().add(group);
		return copy;
	}

	@Test
	void aSecondTemplateOnASystemSchemaReusesThePersonalCopy() {
		Schema copy = personalCopy();
		when(schemaRepository.findById(1L)).thenReturn(Optional.of(systemSchema));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(learner));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(1L))
				.thenReturn(new ArrayList<>(systemSchema.getAttributeGroups()));
		when(schemaRepository.findByCode(SchemaService.personalCopyCode(USER_ID, 1L))).thenReturn(Optional.of(copy));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(2L))
				.thenReturn(new ArrayList<>(copy.getAttributeGroups()));
		when(templateRepository.findBySchemaId(2L)).thenReturn(List.of(Template.builder().id(30L).schema(copy).build()));
		when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		schemaService.createTemplate(1L, request(systemWord.getId()), USER_ID);

		verify(schemaRepository, never()).saveAndFlush(any());
		ArgumentCaptor<Template> saved = ArgumentCaptor.forClass(Template.class);
		verify(templateRepository).save(saved.capture());
		assertThat(saved.getValue().getSchema()).isSameAs(copy);
		// The system attribute id was translated to the copy's attribute of the same name.
		assertThat(saved.getValue().getElements().get(0).getField().getSchemaAttribute().getId()).isEqualTo(200L);
		assertThat(saved.getValue().getCode()).startsWith("TPL_");
	}

	@Test
	void theFirstTemplateOnASystemSchemaCreatesTheCopyWithAStableCode() {
		when(schemaRepository.findById(1L)).thenReturn(Optional.of(systemSchema));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(learner));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(1L))
				.thenReturn(new ArrayList<>(systemSchema.getAttributeGroups()));
		when(schemaRepository.findByCode(SchemaService.personalCopyCode(USER_ID, 1L))).thenReturn(Optional.empty());
		when(schemaRepository.saveAndFlush(any())).thenAnswer(inv -> {
			Schema s = inv.getArgument(0);
			s.setId(2L);
			return s;
		});
		when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		schemaService.createTemplate(1L, request(systemWord.getId()), USER_ID);

		ArgumentCaptor<Schema> clone = ArgumentCaptor.forClass(Schema.class);
		verify(schemaRepository).saveAndFlush(clone.capture());
		assertThat(clone.getValue().getCode()).isEqualTo("CUSTOM_4_BASE_1");
		assertThat(clone.getValue().getUser()).isSameAs(learner);
	}

	@Test
	void updatingRewritesTheLearnersOwnTemplateInPlace() {
		Schema copy = personalCopy();
		Template template = Template.builder().id(30L).schema(copy).code("TPL_1").name("Old").build();
		template.addElement(TemplateElement.builder().type(TemplateElementType.FIELD).side(CardSide.BACK).position(0).build());
		when(templateRepository.findById(30L)).thenReturn(Optional.of(template));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(2L))
				.thenReturn(new ArrayList<>(copy.getAttributeGroups()));
		when(templateRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		schemaService.updateTemplate(2L, 30L, request(200L), USER_ID);

		assertThat(template.getName()).isEqualTo("Mẫu của tôi");
		assertThat(template.getElements()).hasSize(1);
		assertThat(template.getElements().get(0).getSide()).isEqualTo(CardSide.FRONT);
		assertThat(template.getId()).isEqualTo(30L);
		// Only the elements changed: the library order still has to move it to the top.
		verify(templateRepository).touch(eq(30L), any());
	}

	@Test
	void aFieldOfAnotherSchemaIsRefusedInsteadOfCreatingANamelessAttribute() {
		Schema copy = personalCopy();
		Template template = Template.builder().id(30L).schema(copy).code("TPL_1").name("Old").build();
		when(templateRepository.findById(30L)).thenReturn(Optional.of(template));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(2L))
				.thenReturn(new ArrayList<>(copy.getAttributeGroups()));
		when(templateRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		// 100 is the system schema's "word", not the copy's (200).
		assertThatThrownBy(() -> schemaService.updateTemplate(2L, 30L, request(100L), USER_ID))
				.isInstanceOf(vn.ptit.snapvocab.common.exception.BadRequestAlertException.class)
				.hasMessage(SchemaService.UNKNOWN_ATTRIBUTE);
		verify(schemaRepository, never()).saveAndFlush(any());
	}

	@Test
	void aSystemTemplateCannotBeUpdated() {
		Template system = Template.builder().id(31L).schema(systemSchema).code("DEFAULT").name("Default").build();
		when(templateRepository.findById(31L)).thenReturn(Optional.of(system));

		assertThatThrownBy(() -> schemaService.updateTemplate(1L, 31L, request(100L), USER_ID))
				.isInstanceOf(AccessDeniedException.class);
		verify(templateRepository, never()).save(any());
	}

	@Test
	void aTemplateInUseCannotBeDeletedButAnUnusedOneCan() {
		Schema copy = personalCopy();
		Template used = Template.builder().id(30L).schema(copy).code("TPL_1").name("Used").build();
		Template unused = Template.builder().id(32L).schema(copy).code("TPL_2").name("Unused").build();
		when(templateRepository.findById(30L)).thenReturn(Optional.of(used));
		when(templateRepository.findById(32L)).thenReturn(Optional.of(unused));
		when(topicRepository.countByActiveTemplateId(30L)).thenReturn(2L);
		when(topicRepository.countByActiveTemplateId(32L)).thenReturn(0L);

		assertThatThrownBy(() -> schemaService.deleteTemplate(2L, 30L, USER_ID))
				.isInstanceOf(ResourceAlreadyExistException.class)
				.hasMessage(SchemaService.TEMPLATE_IN_USE);
		schemaService.deleteTemplate(2L, 32L, USER_ID);
		verify(templateRepository).delete(unused);
		verify(templateRepository, never()).delete(used);
	}

	@Test
	void theLibraryListsUsageAndTheFieldsOfEachSide() {
		Schema copy = personalCopy();
		Template template = Template.builder().id(30L).schema(copy).code("TPL_1").name("Mine").build();
		TemplateElement front = TemplateElement.builder().type(TemplateElementType.FIELD).side(CardSide.FRONT).position(0).build();
		front.setField(TemplateField.builder().schemaAttribute(copy.getAttributeGroups().iterator().next().getAttributes().get(0)).build());
		TemplateElement back = TemplateElement.builder().type(TemplateElementType.FIELD).side(CardSide.BACK).position(1).build();
		back.setField(TemplateField.builder().fieldLabel("Nghĩa").build());
		template.addElement(front);
		template.addElement(back);
		when(templateRepository.findLibraryTemplates(USER_ID)).thenReturn(List.of(template));
		when(topicRepository.countByActiveTemplateIds(List.of(30L))).thenReturn(List.<Object[]>of(new Object[] {30L, 3L}));

		List<MyTemplateDTO> library = schemaService.getMyTemplates(USER_ID);

		assertThat(library).singleElement().satisfies(entry -> {
			assertThat(entry.usageCount()).isEqualTo(3L);
			assertThat(entry.schemaId()).isEqualTo(2L);
			assertThat(entry.frontFields()).containsExactly("Word");
			assertThat(entry.backFields()).containsExactly("Nghĩa");
		});
	}
}
