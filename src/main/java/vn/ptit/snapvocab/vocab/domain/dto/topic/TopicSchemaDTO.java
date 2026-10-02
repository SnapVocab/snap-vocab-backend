package vn.ptit.snapvocab.vocab.domain.dto.topic;

import java.util.ArrayList;
import java.util.List;
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
public class TopicSchemaDTO {

	private Long topicSchemaId;
	private Long schemaId;
	private Long topicId;
	private String schemaName;
	private String schemaDescription;
	private boolean isShared;
	private long usageCount;

	@Builder.Default
	private List<SchemaAttributeGroupDTO> groups = new ArrayList<>();
}

