package vn.ptit.snapvocab.domain.dto.topic;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicGroupSchemaDTO {

	private Long groupId;
	private String name;
	private String label;
	private boolean multiple;
	private short position;

	@Builder.Default
	private Map<String, TopicAttributeSchemaDTO> attributes = new LinkedHashMap<>();
}
