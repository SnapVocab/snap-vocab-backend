package vn.ptit.snapvocab.vocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.mapper.TopicSchemaMapper;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

@ExtendWith(MockitoExtension.class)
class TopicServiceForkTest {

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private SchemaRepository schemaRepository;

	@Mock
	private SchemaAttributeGroupRepository schemaAttributeGroupRepository;

	@Mock
	private TemplateRepository templateRepository;

	@Mock
	private TopicItemRepository topicItemRepository;

	@Mock
	private TemplateFieldRepository templateFieldRepository;

	@Mock
	private TopicSchemaMapper topicSchemaMapper;

	@InjectMocks
	private TopicService topicService;

	private Topic topic;
	private Schema sharedSchema;
	private SchemaAttributeGroup group;
	private SchemaAttribute attr;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin@test.local", "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		Collection collection = Collection.builder().id(1L).type(CollectionType.SYSTEM).build();
		sharedSchema = Schema.builder().id(100L).name("Shared English").build();
		topic = Topic.builder().id(10L).name("Business Topic").collection(collection).schema(sharedSchema).build();

		group = SchemaAttributeGroup.builder().id(1000L).name("main").schema(sharedSchema).build();
		attr = SchemaAttribute.builder().id(5000L).name("word").group(group).build();
		group.setAttributes(new ArrayList<>(List.of(attr)));
		sharedSchema.getAttributeGroups().add(group);
	}

	@Test
	void forkSystemTopicRejectsANonAdminBeforeCloningAnything() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"learner@test.local", "unused", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
		assertThatThrownBy(() -> topicService.forkTopicSchema(10L)).isInstanceOf(AccessDeniedException.class);
		verify(schemaRepository, never()).save(any());
		verify(topicRepository, never()).save(any());
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void forkTopicSchema_whenNotShared_doesNotClone() {
		when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
		when(topicRepository.countBySchemaId(100L)).thenReturn(1L);
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));
		when(topicSchemaMapper.toDto(any(), any(), any())).thenReturn(
				TopicSchemaDTO.builder().topicId(10L).schemaId(100L).build()
		);

		TopicSchemaDTO result = topicService.forkTopicSchema(10L);

		assertThat(result).isNotNull();
		verify(schemaRepository, never()).save(any());
	}

	@Test
	void forkTopicSchema_whenShared_clonesSchemaAndRemapsValuesAndTemplates() {
		when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
		when(topicRepository.countBySchemaId(100L)).thenReturn(3L);
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));

		// Mock Template on shared schema
		Template template = Template.builder().id(5L).schema(sharedSchema).name("Standard").isDefault(true).build();
		TemplateElement element = TemplateElement.builder().id(6L).template(template).position(0).build();
		TemplateField templateField = TemplateField.builder().id(7L).element(element).schemaAttribute(attr).build();
		element.setField(templateField);
		template.setElements(new ArrayList<>(List.of(element)));
		topic.setActiveTemplate(template);

		when(templateRepository.findBySchemaId(100L)).thenReturn(List.of(template));

		Schema savedForked = Schema.builder().id(200L).name("Business Topic Schema (Custom)").build();
		when(schemaRepository.save(any(Schema.class))).thenReturn(savedForked);

		// Mock Item with EAV value
		TopicItem item = TopicItem.builder().id(1L).topic(topic).build();
		TopicItemAttributeGroup itemGroup = TopicItemAttributeGroup.builder().id(2L).item(item).groupDefinition(group).build();
		TopicItemAttributeValue itemVal = TopicItemAttributeValue.builder().id(3L).groupInstance(itemGroup).attribute(attr).value("hello").build();
		itemGroup.setValues(new ArrayList<>(List.of(itemVal)));
		item.setGroupInstances(new ArrayList<>(List.of(itemGroup)));
		when(topicItemRepository.findByTopicIdWithEav(10L)).thenReturn(List.of(item));

		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(200L)).thenReturn(List.of());
		when(topicRepository.countBySchemaId(200L)).thenReturn(1L);
		when(topicSchemaMapper.toDto(any(), any(), any())).thenReturn(
				TopicSchemaDTO.builder().topicId(10L).schemaId(200L).isShared(false).usageCount(1L).build()
		);

		TopicSchemaDTO result = topicService.forkTopicSchema(10L);

		assertThat(result).isNotNull();
		assertThat(result.getSchemaId()).isEqualTo(200L);
		assertThat(result.isShared()).isFalse();

		ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);
		verify(schemaRepository).save(schemaCaptor.capture());
		Schema capturedSchema = schemaCaptor.getValue();
		assertThat(capturedSchema.getTemplates()).hasSize(1);
		Template capturedTemplate = capturedSchema.getTemplates().get(0);
		assertThat(capturedTemplate.getName()).isEqualTo("Standard");
		assertThat(capturedTemplate.getElements()).hasSize(1);
		assertThat(capturedTemplate.getElements().get(0).getField().getSchemaAttribute().getName()).isEqualTo("word");
		assertThat(capturedTemplate.getElements().get(0).getField().getSchemaAttribute()).isNotEqualTo(attr);

		verify(topicRepository).save(topic);
		assertThat(topic.getSchema()).isEqualTo(savedForked);

		// Verify remapping occurred on Item
		assertThat(itemVal.getAttribute()).isNotEqualTo(attr);
		assertThat(itemVal.getAttribute().getName()).isEqualTo("word");
	}
}

