package vn.ptit.snapvocab.dictionary.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.dto.WordDTO;

@Mapper(componentModel = "spring")
public interface WordMapper extends EntityMapper<WordDTO, Word> {

}

