package vn.ptit.snapvocab.domain.mapper;

import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaAttributeDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SchemaAttributeMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "group", ignore = true)
	@Mapping(target = "values", ignore = true)
	@Mapping(target = "required", defaultValue = "false")
	@Mapping(target = "position", defaultValue = "0")
	SchemaAttribute toEntity(CreateSchemaAttributeDTO dto);

	@Mapping(source = "id", target = "attributeId")
	SchemaAttributeDTO toDto(SchemaAttribute entity);

	List<SchemaAttributeDTO> toDtoList(List<SchemaAttribute> entities);

	@AfterMapping
	default void resolveLabel(@MappingTarget SchemaAttribute entity, CreateSchemaAttributeDTO dto) {
		if (entity.getLabel() == null || entity.getLabel().isBlank()) {
			entity.setLabel(SchemaMapperUtils.formatNameAsLabel(dto.getName()));
		}
	}

	@AfterMapping
	default void resolveDtoLabel(@MappingTarget SchemaAttributeDTO dto, SchemaAttribute entity) {
		if (dto.getLabel() == null || dto.getLabel().isBlank()) {
			dto.setLabel(SchemaMapperUtils.formatNameAsLabel(entity.getName()));
		}
	}
}
