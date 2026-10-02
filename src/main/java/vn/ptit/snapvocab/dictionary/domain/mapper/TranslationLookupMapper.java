package vn.ptit.snapvocab.dictionary.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.dictionary.domain.Translation;
import vn.ptit.snapvocab.dictionary.domain.dto.TranslationLookupDTO;

@Mapper(componentModel = "spring")
public interface TranslationLookupMapper {

    TranslationLookupDTO toDto(Translation translation);
}

