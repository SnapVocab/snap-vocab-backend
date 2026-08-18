package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.domain.Translation;
import vn.ptit.snapvocab.domain.dto.word.TranslationLookupDTO;

@Mapper(componentModel = "spring")
public interface TranslationLookupMapper {

    TranslationLookupDTO toDto(Translation translation);
}
