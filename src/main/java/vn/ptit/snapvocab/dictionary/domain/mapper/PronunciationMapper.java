package vn.ptit.snapvocab.dictionary.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.dictionary.domain.Pronunciation;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.dto.PronunciationDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDTO;

@Mapper(componentModel = "spring")
public interface PronunciationMapper extends EntityMapper<PronunciationDTO, Pronunciation> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	PronunciationDTO toDto(Pronunciation s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);
}

