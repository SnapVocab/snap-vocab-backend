package vn.ptit.snapvocab.dictionary.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.dictionary.domain.Pronunciation;
import vn.ptit.snapvocab.dictionary.domain.dto.PronunciationLookupDTO;

@Mapper(componentModel = "spring")
public interface PronunciationLookupMapper {

    PronunciationLookupDTO toDto(Pronunciation pronunciation);
}

