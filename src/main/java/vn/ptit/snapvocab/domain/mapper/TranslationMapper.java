package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.Translation;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.word.TranslationDTO;
import vn.ptit.snapvocab.domain.dto.word.WordDTO;

@Mapper(componentModel = "spring")
public interface TranslationMapper extends EntityMapper<TranslationDTO, Translation> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	TranslationDTO toDto(Translation s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);
}
