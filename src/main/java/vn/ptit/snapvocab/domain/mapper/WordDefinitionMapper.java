package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.domain.WordDefinition;
import vn.ptit.snapvocab.domain.dto.word.WordDefinitionDTO;

@Mapper(componentModel = "spring")
public interface WordDefinitionMapper extends EntityMapper<WordDefinitionDTO, WordDefinition> {

}
