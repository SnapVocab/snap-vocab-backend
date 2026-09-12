package vn.ptit.snapvocab.domain.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.TopicSchema;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaDTO;

class TopicSchemaMapperTest {

	private SchemaAttributeMapper attributeMapper;
	private SchemaAttributeGroupMapper groupMapper;
	private TopicSchemaMapper schemaMapper;

	@BeforeEach
	void setUp() {
		attributeMapper = new SchemaAttributeMapperImpl();
		groupMapper = new SchemaAttributeGroupMapperImpl();
		schemaMapper = new TopicSchemaMapperImpl();

		ReflectionTestUtils.setField(groupMapper, "schemaAttributeMapper", attributeMapper);
		ReflectionTestUtils.setField(schemaMapper, "schemaAttributeGroupMapper", groupMapper);
	}

	@Test
	void shouldMapCreateDtoToEntityWithHierarchyAndBackReferences() {
		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.topicId(1L)
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("word_details")
								.multiple(false)
								.position((short) 1)
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder()
												.name("part_of_speech")
												.dataType("TEXT")
												.required(true)
												.position(1)
												.build(),
										CreateSchemaAttributeDTO.builder()
												.name("phonetic")
												.label("IPA Phonetics")
												.dataType("TEXT")
												.required(false)
												.position(2)
												.build()
								))
								.build()
				))
				.build();

		TopicSchema schema = schemaMapper.toEntity(dto);

		assertThat(schema).isNotNull();
		assertThat(schema.getAttributeGroups()).hasSize(1);

		SchemaAttributeGroup group = schema.getAttributeGroups().iterator().next();
		assertThat(group.getName()).isEqualTo("word_details");
		assertThat(group.getLabel()).isEqualTo("Word Details");
		assertThat(group.getSchema()).isSameAs(schema);
		assertThat(group.getAttributes()).hasSize(2);

		SchemaAttribute attr1 = group.getAttributes().get(0);
		assertThat(attr1.getName()).isEqualTo("part_of_speech");
		assertThat(attr1.getLabel()).isEqualTo("Part Of Speech");
		assertThat(attr1.getGroup()).isSameAs(group);
		assertThat(attr1.isRequired()).isTrue();

		SchemaAttribute attr2 = group.getAttributes().get(1);
		assertThat(attr2.getName()).isEqualTo("phonetic");
		assertThat(attr2.getLabel()).isEqualTo("IPA Phonetics");
		assertThat(attr2.getGroup()).isSameAs(group);
		assertThat(attr2.isRequired()).isFalse();
	}

	@Test
	void shouldMapEntityToDto() {
		Topic topic = Topic.builder().id(99L).name("English Vocab").build();
		TopicSchema schema = TopicSchema.builder().id(10L).topic(topic).build();

		SchemaAttributeGroup group = SchemaAttributeGroup.builder()
				.id(100L)
				.schema(schema)
				.name("grammar_group")
				.label("Grammar")
				.multiple(true)
				.position((short) 0)
				.build();

		SchemaAttribute attr = SchemaAttribute.builder()
				.id(200L)
				.group(group)
				.name("tense")
				.dataType("TEXT")
				.required(true)
				.position((short) 1)
				.build();

		group.getAttributes().add(attr);
		schema.getAttributeGroups().add(group);

		TopicSchemaDTO dto = schemaMapper.toDto(schema);

		assertThat(dto).isNotNull();
		assertThat(dto.getSchemaId()).isEqualTo(10L);
		assertThat(dto.getTopicId()).isEqualTo(99L);
		assertThat(dto.getGroups()).hasSize(1);

		var groupDTO = dto.getGroups().get(0);
		assertThat(groupDTO.getGroupId()).isEqualTo(100L);
		assertThat(groupDTO.getName()).isEqualTo("grammar_group");
		assertThat(groupDTO.getLabel()).isEqualTo("Grammar");
		assertThat(groupDTO.isMultiple()).isTrue();
		assertThat(groupDTO.getAttributes()).hasSize(1);

		var attrDTO = groupDTO.getAttributes().get(0);
		assertThat(attrDTO.getAttributeId()).isEqualTo(200L);
		assertThat(attrDTO.getName()).isEqualTo("tense");
		assertThat(attrDTO.getLabel()).isEqualTo("Tense");
		assertThat(attrDTO.getDataType()).isEqualTo("TEXT");
		assertThat(attrDTO.isRequired()).isTrue();
	}

	@Test
	void shouldUpdateExistingEntityFromDto() {
		Topic topic = Topic.builder().id(50L).build();
		TopicSchema schema = TopicSchema.builder().id(5L).topic(topic).build();

		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("definitions")
								.multiple(true)
								.position((short) 1)
								.build()
				))
				.build();

		schemaMapper.updateEntityFromDto(dto, schema);

		assertThat(schema.getAttributeGroups()).hasSize(1);
		SchemaAttributeGroup group = schema.getAttributeGroups().iterator().next();
		assertThat(group.getName()).isEqualTo("definitions");
		assertThat(group.getLabel()).isEqualTo("Definitions");
		assertThat(group.getSchema()).isSameAs(schema);
	}
}
