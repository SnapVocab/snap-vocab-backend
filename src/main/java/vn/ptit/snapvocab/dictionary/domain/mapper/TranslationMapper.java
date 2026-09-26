package vn.ptit.snapvocab.dictionary.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.dictionary.domain.Translation;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.dto.TranslationDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDTO;

@Mapper(componentModel = "spring")
public interface TranslationMapper extends EntityMapper<TranslationDTO, Translation> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	TranslationDTO toDto(Translation s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);
}

