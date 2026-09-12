package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.WordDefinition;
import vn.ptit.snapvocab.domain.dto.word.MeaningDTO;
import vn.ptit.snapvocab.domain.enumeration.CodeLabelMapper;
import vn.ptit.snapvocab.domain.enumeration.PosLabel;
import vn.ptit.snapvocab.domain.enumeration.SubPosLabel;

@Mapper(componentModel = "spring", uses = LinksParser.class)
public interface MeaningMapper {

    @Mapping(target = "definition", source = "definition.definition")
    @Mapping(target = "definitionLang", source = "definition.definitionLang")
    @Mapping(target = "example", source = "example")
    @Mapping(target = "links", source = "definition.links")
    @Mapping(target = "pos", source = "definition.pos", qualifiedByName = "mapPos")
    @Mapping(target = "subPos", source = "definition.subPos", qualifiedByName = "mapSubPos")
    MeaningDTO toDto(WordDefinition wordDefinition);

    @Named("mapPos")
    default String mapPos(String code) {
        return CodeLabelMapper.fromCode(code, PosLabel.class);
    }

    @Named("mapSubPos")
    default String mapSubPos(String code) {
        return CodeLabelMapper.fromCode(code, SubPosLabel.class);
    }
}
