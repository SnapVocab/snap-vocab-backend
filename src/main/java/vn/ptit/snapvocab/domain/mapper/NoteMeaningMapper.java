package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import vn.ptit.snapvocab.domain.NoteMeaning;
import vn.ptit.snapvocab.domain.dto.note.NoteMeaningDTO;

@Mapper(componentModel = "spring")
public interface NoteMeaningMapper extends EntityMapper<NoteMeaningDTO, NoteMeaning> {

	@Override
	@Mapping(target = "note", ignore = true)
	NoteMeaning toEntity(NoteMeaningDTO dto);

	@Override
	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
	@Mapping(target = "note", ignore = true)
	void partialUpdate(@MappingTarget NoteMeaning entity, NoteMeaningDTO dto);
}
