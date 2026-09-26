package vn.ptit.snapvocab.dictionary.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.WordRelation;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.WordRelationDTO;

@Mapper(componentModel = "spring")
public interface WordRelationMapper extends EntityMapper<WordRelationDTO, WordRelation> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	WordRelationDTO toDto(WordRelation s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);
}
