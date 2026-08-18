package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.domain.Collection;
import vn.ptit.snapvocab.domain.dto.collection.CollectionDTO;

@Mapper(componentModel = "spring")
public interface CollectionMapper extends EntityMapper<CollectionDTO, Collection> {

}
