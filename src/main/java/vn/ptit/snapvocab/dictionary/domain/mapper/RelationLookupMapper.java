package vn.ptit.snapvocab.dictionary.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.dictionary.domain.WordRelation;
import vn.ptit.snapvocab.dictionary.domain.dto.RelationLookupDTO;
import vn.ptit.snapvocab.dictionary.domain.enumeration.CodeLabelMapper;
import vn.ptit.snapvocab.dictionary.domain.enumeration.RelationLabel;

@Mapper(componentModel = "spring")
public interface RelationLookupMapper {

    @Mapping(target = "relationType", source = "relationType", qualifiedByName = "mapRelationType")
    RelationLookupDTO toDto(WordRelation wordRelation);

    @Named("mapRelationType")
    default String mapRelationType(String code) {
        return CodeLabelMapper.fromCode(code, RelationLabel.class);
    }
}

