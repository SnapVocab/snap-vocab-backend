package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.domain.Pronunciation;
import vn.ptit.snapvocab.domain.dto.PronunciationLookupDTO;

@Mapper(componentModel = "spring")
public interface PronunciationLookupMapper {

    PronunciationLookupDTO toDto(Pronunciation pronunciation);
}
