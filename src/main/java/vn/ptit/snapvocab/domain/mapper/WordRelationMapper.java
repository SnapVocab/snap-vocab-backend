package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.WordRelation;
import vn.ptit.snapvocab.domain.dto.WordDTO;
import vn.ptit.snapvocab.domain.dto.WordRelationDTO;

@Mapper(componentModel = "spring")
public interface WordRelationMapper extends EntityMapper<WordRelationDTO, WordRelation> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	WordRelationDTO toDto(WordRelation s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);
}