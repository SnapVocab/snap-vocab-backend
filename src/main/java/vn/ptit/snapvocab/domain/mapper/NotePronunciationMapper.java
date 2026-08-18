package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import vn.ptit.snapvocab.domain.NotePronunciation;
import vn.ptit.snapvocab.domain.dto.note.NotePronunciationDTO;

@Mapper(componentModel = "spring")
public interface NotePronunciationMapper extends EntityMapper<NotePronunciationDTO, NotePronunciation> {

	@Override
	@Mapping(target = "note", ignore = true)
	NotePronunciation toEntity(NotePronunciationDTO dto);

	@Override
	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
	@Mapping(target = "note", ignore = true)
	void partialUpdate(@MappingTarget NotePronunciation entity, NotePronunciationDTO dto);
}
