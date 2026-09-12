package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.Definition;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.WordDefinition;
import vn.ptit.snapvocab.domain.dto.word.DefinitionDTO;
import vn.ptit.snapvocab.domain.dto.word.WordDTO;
import vn.ptit.snapvocab.domain.dto.word.WordDefinitionDTO;

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
