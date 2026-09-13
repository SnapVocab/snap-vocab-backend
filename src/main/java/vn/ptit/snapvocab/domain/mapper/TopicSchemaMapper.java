package vn.ptit.snapvocab.domain.mapper;

import java.util.List;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.TopicSchema;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true), uses = {SchemaAttributeGroupMapper.class})
public interface TopicSchemaMapper {

	@Mapping(source = "id", target = "topicSchemaId")
	@Mapping(source = "schema.id", target = "schemaId")
	@Mapping(source = "topic.id", target = "topicId")
	@Mapping(source = "schema.name", target = "schemaName")
	@Mapping(source = "schema.description", target = "schemaDescription")
	@Mapping(source = "schema.attributeGroups", target = "groups")
	TopicSchemaDTO toDto(TopicSchema entity);

	@Mapping(source = "topicSchema.id", target = "topicSchemaId")
	@Mapping(source = "topicSchema.schema.id", target = "schemaId")
	@Mapping(source = "topicSchema.topic.id", target = "topicId")
	@Mapping(source = "topicSchema.schema.name", target = "schemaName")
	@Mapping(source = "topicSchema.schema.description", target = "schemaDescription")
	@Mapping(source = "groups", target = "groups")
	TopicSchemaDTO toDto(TopicSchema topicSchema, List<SchemaAttributeGroup> groups);
}
