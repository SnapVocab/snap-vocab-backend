package vn.ptit.snapvocab.domain.mapper;

import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.TopicSchema;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true), uses = {SchemaAttributeGroupMapper.class})
public interface TopicSchemaMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "topic", ignore = true)
	@Mapping(source = "schemaGroups", target = "attributeGroups")
	TopicSchema toEntity(CreateTopicSchemaDTO dto);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "topic", ignore = true)
	@Mapping(source = "schemaGroups", target = "attributeGroups")
	void updateEntityFromDto(CreateTopicSchemaDTO dto, @MappingTarget TopicSchema schema);

	@Mapping(source = "id", target = "schemaId")
	@Mapping(source = "topic.id", target = "topicId")
	@Mapping(source = "attributeGroups", target = "groups")
	TopicSchemaDTO toDto(TopicSchema entity);

	@Mapping(source = "schema.id", target = "schemaId")
	@Mapping(source = "schema.topic.id", target = "topicId")
	@Mapping(source = "groups", target = "groups")
	TopicSchemaDTO toDto(TopicSchema schema, List<SchemaAttributeGroup> groups);

	@AfterMapping
	default void linkGroups(@MappingTarget TopicSchema schema) {
		if (schema.getAttributeGroups() != null) {
			schema.getAttributeGroups().forEach(group -> group.setSchema(schema));
		}
	}
}
