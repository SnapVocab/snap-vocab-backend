package vn.ptit.snapvocab.domain.mapper;

import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.Translation;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.domain.dto.TranslationLookupDTO;

@Mapper(componentModel = "spring", uses = {
        PronunciationLookupMapper.class,
        RelationLookupMapper.class,
        MeaningMapper.class
})
public interface LookupResultMapper {

    @Mapping(target = "exists", constant = "true")
    @Mapping(target = "langCode", constant = "vi")
    @Mapping(target = "langName", constant = "Tiếng Việt")
    @Mapping(target = "pronunciations", source = "pronunciations")
    @Mapping(target = "translations", source = "translations", qualifiedByName = "filterVi")
    @Mapping(target = "relations", source = "wordRelations")
    @Mapping(target = "meanings", source = "wordDefinitions")
    LookupResultDTO toLookupResult(Word word);

    List<LookupResultDTO> toLookupResult(List<Word> words);

    @Named("filterVi")
    default List<TranslationLookupDTO> filterVi(Set<Translation> translations) {
        if (translations == null) return List.of();
        return translations.stream()
                .filter(t -> "vi".equals(t.getLangCode()))
                .map(t -> new TranslationLookupDTO(t.getLangCode(), t.getTranslation()))
                .toList();
    }
}
