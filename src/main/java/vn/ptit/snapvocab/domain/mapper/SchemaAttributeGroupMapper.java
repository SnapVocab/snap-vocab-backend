package vn.ptit.snapvocab.domain.mapper;

import java.util.Collection;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaAttributeGroupDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true), uses = {SchemaAttributeMapper.class})
public interface SchemaAttributeGroupMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "schema", ignore = true)
	@Mapping(target = "instances", ignore = true)
	@Mapping(source = "multiple", target = "multiple")
	SchemaAttributeGroup toEntity(CreateSchemaAttributeGroupDTO dto);

	@Mapping(source = "id", target = "groupId")
	SchemaAttributeGroupDTO toDto(SchemaAttributeGroup entity);

	List<SchemaAttributeGroupDTO> toDtoList(Collection<SchemaAttributeGroup> entities);

	@AfterMapping
	default void linkAttributesAndResolveLabel(@MappingTarget SchemaAttributeGroup group, CreateSchemaAttributeGroupDTO dto) {
		if (group.getLabel() == null || group.getLabel().isBlank()) {
			group.setLabel(SchemaMapperUtils.formatNameAsLabel(dto.getName()));
		}
		if (group.getAttributes() != null) {
			group.getAttributes().forEach(attr -> attr.setGroup(group));
		}
	}

	@AfterMapping
	default void resolveDtoLabel(@MappingTarget SchemaAttributeGroupDTO dto, SchemaAttributeGroup entity) {
		if (dto.getLabel() == null || dto.getLabel().isBlank()) {
			dto.setLabel(SchemaMapperUtils.formatNameAsLabel(entity.getName()));
		}
	}
}
