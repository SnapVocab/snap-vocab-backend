package vn.ptit.snapvocab.domain.mapper;

import java.util.List;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true), uses = {SchemaAttributeGroupMapper.class})
public interface TopicSchemaMapper {

	@Mapping(source = "schema.id", target = "schemaId")
	@Mapping(source = "topic.id", target = "topicId")
	@Mapping(source = "schema.name", target = "schemaName")
	@Mapping(source = "schema.description", target = "schemaDescription")
	@Mapping(source = "groups", target = "groups")
	TopicSchemaDTO toDto(Topic topic, Schema schema, List<SchemaAttributeGroup> groups);
}

