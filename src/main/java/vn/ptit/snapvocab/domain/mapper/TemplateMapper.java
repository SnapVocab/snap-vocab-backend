package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.ptit.snapvocab.domain.Template;
import vn.ptit.snapvocab.domain.TemplateElement;
import vn.ptit.snapvocab.domain.TemplateField;
import vn.ptit.snapvocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.domain.dto.template.TemplateElementDTO;
import vn.ptit.snapvocab.domain.dto.template.TemplateFieldDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TemplateMapper {

	@Mapping(source = "schema.id", target = "schemaId")
	@Mapping(source = "isDefault", target = "isDefault")
	TemplateDTO toDto(Template template);

	TemplateElementDTO toElementDto(TemplateElement element);

	@Mapping(source = "schemaAttribute.id", target = "schemaAttributeId")
	@Mapping(source = "schemaAttribute.name", target = "attributeName")
	TemplateFieldDTO toFieldDto(TemplateField field);
}
