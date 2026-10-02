package vn.ptit.snapvocab.dictionary.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.dictionary.domain.Definition;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.WordDefinition;
import vn.ptit.snapvocab.dictionary.domain.dto.DefinitionDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDefinitionDTO;

@Mapper(componentModel = "spring")
public interface DefinitionMapper extends EntityMapper<DefinitionDTO, Definition> {

	@Mapping(target = "word", source = "word", qualifiedByName = "wordId")
	@Mapping(target = "definition", source = "definition", qualifiedByName = "definitionId")
	WordDefinitionDTO toDto(WordDefinition s);

	@Named("wordId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	WordDTO toDtoWordId(Word word);

	@Named("definitionId")
	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "id")
	DefinitionDTO toDtoDefinitionId(Definition definition);
}

