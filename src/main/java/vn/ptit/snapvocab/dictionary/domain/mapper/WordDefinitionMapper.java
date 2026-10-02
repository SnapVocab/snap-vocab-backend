package vn.ptit.snapvocab.dictionary.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.dictionary.domain.WordDefinition;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDefinitionDTO;

@Mapper(componentModel = "spring")
public interface WordDefinitionMapper extends EntityMapper<WordDefinitionDTO, WordDefinition> {

}

