package vn.ptit.snapvocab.vocab.domain.dto.topic;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class TopicItemGroupData {

	private final Long groupId;
	private final String groupName;
	private final boolean multiple;

	private final Map<Long, TopicItemGroupInstanceData> instances =
			new LinkedHashMap<>();
}

