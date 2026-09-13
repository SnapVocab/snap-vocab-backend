package vn.ptit.snapvocab.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.repository.TopicSchemaRepository;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

	@Mock
	private SchemaRepository schemaRepository;

	@Mock
	private TopicSchemaRepository topicSchemaRepository;

	@Mock
	private SchemaAttributeGroupRepository schemaAttributeGroupRepository;

	@Mock
	private TopicItemAttributeValueRepository topicItemAttributeValueRepository;

	@Mock
	private TemplateFieldRepository templateFieldRepository;

	@Mock
	private SchemaMapper schemaMapper;

	@InjectMocks
	private SchemaService schemaService;

	private Schema schema;
	private SchemaAttributeGroup group;
	private SchemaAttribute attr;

	@BeforeEach
	void setUp() {
		schema = Schema.builder().id(100L).name("English Schema").build();
		group = SchemaAttributeGroup.builder().id(1000L).name("main").schema(schema).build();
		attr = SchemaAttribute.builder().id(5000L).name("word").group(group).build();
		group.setAttributes(new ArrayList<>(List.of(attr)));
		schema.getAttributeGroups().add(group);
	}

	@Test
	void updateSchema_whenDeletingAttributeInUse_throwsIllegalStateException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));
		when(topicItemAttributeValueRepository.countByAttributeId(5000L)).thenReturn(10L);

		// Incoming DTO does NOT contain 'word' (user attempts to delete it)
		CreateSchemaDTO updateDto = CreateSchemaDTO.builder()
				.name("English Schema Updated")
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("main")
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder().name("phonetic").build()
								))
								.build()
				))
				.build();

		assertThatThrownBy(() -> schemaService.updateSchema(100L, updateDto))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Cannot delete attribute 'word'");

		verify(schemaRepository, never()).save(any());
	}

	@Test
	void updateSchema_whenAddingNewAttribute_succeeds() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));
		when(schemaRepository.save(schema)).thenReturn(schema);
		when(schemaMapper.toDto(any(), any())).thenReturn(
				SchemaDTO.builder().id(100L).name("English Schema Updated").build()
		);

		// Incoming DTO keeps 'word' and adds 'phonetic'
		CreateSchemaDTO updateDto = CreateSchemaDTO.builder()
				.name("English Schema Updated")
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("main")
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder().name("word").build(),
										CreateSchemaAttributeDTO.builder().name("phonetic").build()
								))
								.build()
				))
				.build();

		SchemaDTO result = schemaService.updateSchema(100L, updateDto);

		assertThat(result).isNotNull();
		verify(schemaMapper).updateEntityFromDto(updateDto, schema);
		verify(schemaRepository).save(schema);
	}

	@Test
	void deleteSchema_whenAssignedToTopics_throwsIllegalStateException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));
		when(topicSchemaRepository.countBySchemaId(100L)).thenReturn(2L);

		assertThatThrownBy(() -> schemaService.deleteSchema(100L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("currently assigned to 2 topic(s)");

		verify(schemaRepository, never()).delete(any());
	}
}
