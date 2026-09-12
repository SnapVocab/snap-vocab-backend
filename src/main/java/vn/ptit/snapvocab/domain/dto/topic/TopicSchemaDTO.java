package vn.ptit.snapvocab.domain.dto.topic;

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

	private Long schemaId;
	private Long topicId;

	@Builder.Default
	private List<SchemaAttributeGroupDTO> groups = new ArrayList<>();
}
