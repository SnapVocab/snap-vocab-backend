package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.Pronunciation;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.PronunciationDTO;
import vn.ptit.snapvocab.domain.dto.WordDTO;

@Mapper(componentModel = "spring")
public interface PronunciationMapper extends EntityMapper<PronunciationDTO, Pronunciation> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	PronunciationDTO toDto(Pronunciation s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);
}
