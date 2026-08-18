package vn.ptit.snapvocab.domain.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;

@Mapper(componentModel = "spring")
public interface TopicMapper extends EntityMapper<TopicDTO, Topic> {

	@Mapping(source = "parent.id", target = "parentId")
	@Mapping(target = "children", ignore = true)
	TopicDTO toDto(Topic topic);
}
