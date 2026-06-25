package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.WordDTO;

@Mapper(componentModel = "spring")
public interface WordMapper extends EntityMapper<WordDTO, Word> {

}
