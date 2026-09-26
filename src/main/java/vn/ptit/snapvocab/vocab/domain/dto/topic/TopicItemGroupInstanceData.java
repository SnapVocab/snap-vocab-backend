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
public class TopicItemGroupInstanceData {

	private final Long groupInstanceId;
	private final int position;

	private final Map<String, Object> attributes =
			new LinkedHashMap<>();
}

