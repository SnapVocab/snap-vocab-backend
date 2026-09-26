package vn.ptit.snapvocab.vocab.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.dto.collection.CollectionDTO;

@Mapper(componentModel = "spring")
public interface CollectionMapper extends EntityMapper<CollectionDTO, Collection> {

	@Override
	@Mapping(source = "owner.id", target = "ownerId")
	CollectionDTO toDto(Collection entity);

	@Override
	@Mapping(target = "topics", ignore = true)
	@Mapping(target = "owner", ignore = true)
	Collection toEntity(CollectionDTO dto);
}

