package vn.ptit.snapvocab.vocab.domain.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateElementDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateFieldDTO;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TemplateMapper {

	@Mapping(source = "schema.id", target = "schemaId")
	@Mapping(source = "isDefault", target = "isDefault")
	TemplateDTO toDto(Template template);

	TemplateElementDTO toElementDto(TemplateElement element);

	@Mapping(source = "schemaAttribute.id", target = "schemaAttributeId")
	@Mapping(source = "schemaAttribute.name", target = "attributeName")
	TemplateFieldDTO toFieldDto(TemplateField field);

	/**
	 * Hidden fields are server-only semantic bindings: drop the whole FIELD element so clients
	 * render exactly what they did before the binding existed.
	 */
	default List<TemplateElementDTO> toElementDtos(List<TemplateElement> elements) {
		if (elements == null) {
			return null;
		}
		return elements.stream()
				.filter(element -> element.getField() == null || !element.getField().isHidden())
				.map(this::toElementDto)
				.collect(Collectors.toCollection(ArrayList::new));
	}
}

