package vn.ptit.snapvocab.vocab.domain.mapper;

import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.SchemaDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true), uses = {SchemaAttributeGroupMapper.class, TemplateMapper.class})
public interface SchemaMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "topics", ignore = true)
	@Mapping(target = "templates", ignore = true)
	@Mapping(target = "user", ignore = true)
	@Mapping(source = "schemaGroups", target = "attributeGroups")
	Schema toEntity(CreateSchemaDTO dto);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "topics", ignore = true)
	@Mapping(target = "templates", ignore = true)
	@Mapping(target = "user", ignore = true)
	@Mapping(source = "schemaGroups", target = "attributeGroups")
	void updateEntityFromDto(CreateSchemaDTO dto, @MappingTarget Schema schema);


	@Mapping(source = "user.id", target = "userId")
	@Mapping(source = "attributeGroups", target = "groups")
	SchemaDTO toDto(Schema entity);

	@Mapping(source = "schema.id", target = "id")
	@Mapping(source = "schema.code", target = "code")
	@Mapping(source = "schema.isSystem", target = "isSystem")
	@Mapping(source = "schema.user.id", target = "userId")
	@Mapping(source = "schema.name", target = "name")
	@Mapping(source = "schema.description", target = "description")
	@Mapping(source = "schema.createdAt", target = "createdAt")
	@Mapping(source = "schema.updatedAt", target = "updatedAt")
	@Mapping(source = "schema.templates", target = "templates")
	@Mapping(source = "groups", target = "groups")
	SchemaDTO toDto(Schema schema, List<SchemaAttributeGroup> groups);

	@AfterMapping
	default void linkGroups(@MappingTarget Schema schema) {
		if (schema.getAttributeGroups() != null) {
			schema.getAttributeGroups().forEach(group -> group.setSchema(schema));
		}
	}
}

