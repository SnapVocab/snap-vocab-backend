package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.ptit.snapvocab.domain.WordRelation;
import vn.ptit.snapvocab.domain.dto.word.RelationLookupDTO;
import vn.ptit.snapvocab.domain.enumeration.CodeLabelMapper;
import vn.ptit.snapvocab.domain.enumeration.RelationLabel;

@Mapper(componentModel = "spring")
public interface RelationLookupMapper {

    @Mapping(target = "relationType", source = "relationType", qualifiedByName = "mapRelationType")
    RelationLookupDTO toDto(WordRelation wordRelation);

    @Named("mapRelationType")
    default String mapRelationType(String code) {
        return CodeLabelMapper.fromCode(code, RelationLabel.class);
    }
}
