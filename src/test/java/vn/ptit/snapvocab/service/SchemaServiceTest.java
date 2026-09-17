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
import org.springframework.security.access.AccessDeniedException;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.repository.TopicRepository;
import vn.ptit.snapvocab.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

	@Mock
	private SchemaRepository schemaRepository;

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private SchemaAttributeGroupRepository schemaAttributeGroupRepository;

	@Mock
	private TopicItemAttributeValueRepository topicItemAttributeValueRepository;

	@Mock
	private TemplateFieldRepository templateFieldRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private SchemaMapper schemaMapper;

	@InjectMocks
	private SchemaService schemaService;

	private User owner;
	private Schema schema;
	private SchemaAttributeGroup group;
	private SchemaAttribute attr;

	@BeforeEach
	void setUp() {
		owner = User.builder().id(1L).email("owner@test.com").password("testpass").build();
		schema = Schema.builder().id(100L).name("English Schema").user(owner).isSystem(false).build();
		group = SchemaAttributeGroup.builder().id(1000L).name("main").schema(schema).build();
		attr = SchemaAttribute.builder().id(5000L).name("word").group(group).build();
		group.setAttributes(new ArrayList<>(List.of(attr)));
		schema.getAttributeGroups().add(group);
	}

	@Test
	void createSchema_assignsUserAndIsSystemFalse() {
		CreateSchemaDTO createDto = CreateSchemaDTO.builder().name("Custom Schema").build();
		Schema createdSchema = Schema.builder().id(200L).name("Custom Schema").build();

		when(schemaMapper.toEntity(createDto)).thenReturn(createdSchema);
		when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
		when(schemaRepository.save(any(Schema.class))).thenAnswer(invocation -> {
			Schema s = invocation.getArgument(0);
			s.setId(200L);
			return s;
		});
		when(schemaRepository.findById(200L)).thenReturn(Optional.of(createdSchema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(200L)).thenReturn(List.of());
		when(schemaMapper.toDto(any(), any())).thenReturn(SchemaDTO.builder().id(200L).userId(1L).build());

		SchemaDTO result = schemaService.createSchema(createDto, 1L);

		assertThat(result).isNotNull();
		assertThat(createdSchema.getUser()).isEqualTo(owner);
		assertThat(createdSchema.isSystem()).isFalse();
	}

	@Test
	void getSchemaById_whenUserIsOwner_returnsDto() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));
		when(schemaMapper.toDto(schema, List.of(group))).thenReturn(SchemaDTO.builder().id(100L).userId(1L).build());

		SchemaDTO result = schemaService.getSchemaById(100L, 1L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(100L);
	}

	@Test
	void getSchemaById_whenUserIsNotOwner_throwsAccessDeniedException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));

		assertThatThrownBy(() -> schemaService.getSchemaById(100L, 2L))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("do not have permission to access");
	}

	@Test
	void getSchemaById_whenSystemSchema_accessibleByAnyUser() {
		Schema systemSchema = Schema.builder().id(101L).name("System English").isSystem(true).build();
		when(schemaRepository.findById(101L)).thenReturn(Optional.of(systemSchema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(101L)).thenReturn(List.of());
		when(schemaMapper.toDto(any(), any())).thenReturn(SchemaDTO.builder().id(101L).isSystem(true).build());

		SchemaDTO result = schemaService.getSchemaById(101L, 2L);

		assertThat(result).isNotNull();
		assertThat(result.isSystem()).isTrue();
	}

	@Test
	void getAllSchemas_whenRegularUser_returnsAccessibleSchemas() {
		when(schemaRepository.findAccessibleSchemas(1L)).thenReturn(List.of(schema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));
		when(schemaMapper.toDto(schema, List.of(group))).thenReturn(SchemaDTO.builder().id(100L).userId(1L).build());

		List<SchemaDTO> results = schemaService.getAllSchemas(1L);

		assertThat(results).hasSize(1);
		verify(schemaRepository).findAccessibleSchemas(1L);
		verify(schemaRepository, never()).findAll();
	}

	@Test
	void getAllSchemas_whenUnauthenticated_returnsSystemSchemas() {
		Schema systemSchema = Schema.builder().id(101L).name("System English").isSystem(true).build();
		when(schemaRepository.findByIsSystemTrue()).thenReturn(List.of(systemSchema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(101L)).thenReturn(List.of());
		when(schemaMapper.toDto(any(), any())).thenReturn(SchemaDTO.builder().id(101L).isSystem(true).build());

		List<SchemaDTO> results = schemaService.getAllSchemas(null);

		assertThat(results).hasSize(1);
		verify(schemaRepository).findByIsSystemTrue();
	}

	@Test
	void updateSchema_whenDeletingAttributeInUse_throwsIllegalStateException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));
		when(schemaAttributeGroupRepository.findBySchemaIdWithAttributes(100L)).thenReturn(List.of(group));
		when(topicItemAttributeValueRepository.countByAttributeId(5000L)).thenReturn(10L);

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

		assertThatThrownBy(() -> schemaService.updateSchema(100L, updateDto, 1L))
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
				SchemaDTO.builder().id(100L).name("English Schema Updated").userId(1L).build()
		);

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

		SchemaDTO result = schemaService.updateSchema(100L, updateDto, 1L);

		assertThat(result).isNotNull();
		verify(schemaMapper).updateEntityFromDto(updateDto, schema);
		verify(schemaRepository).save(schema);
	}

	@Test
	void updateSchema_whenUserIsNotOwner_throwsAccessDeniedException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));

		CreateSchemaDTO updateDto = CreateSchemaDTO.builder().name("Hacked").build();

		assertThatThrownBy(() -> schemaService.updateSchema(100L, updateDto, 999L))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("do not have permission to modify");

		verify(schemaRepository, never()).save(any());
	}

	@Test
	void deleteSchema_whenAssignedToTopics_throwsIllegalStateException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));
		when(topicRepository.countBySchemaId(100L)).thenReturn(2L);

		assertThatThrownBy(() -> schemaService.deleteSchema(100L, 1L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("currently assigned to 2 topic(s)");

		verify(schemaRepository, never()).delete(any());
	}

	@Test
	void deleteSchema_whenUserIsNotOwner_throwsAccessDeniedException() {
		when(schemaRepository.findById(100L)).thenReturn(Optional.of(schema));

		assertThatThrownBy(() -> schemaService.deleteSchema(100L, 999L))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("do not have permission to modify");

		verify(schemaRepository, never()).delete(any());
	}
}
