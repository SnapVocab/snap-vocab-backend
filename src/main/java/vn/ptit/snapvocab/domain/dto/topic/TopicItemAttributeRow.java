package vn.ptit.snapvocab.domain.dto.topic;

import java.time.Instant;
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
public class TopicItemAttributeRow {

	private Long topicItemId;
	private Instant itemCreatedAt;
	private Long groupId;
	private String groupName;
	private String groupLabel;
	private boolean groupMultiple;
	private Long groupInstanceId;
	private short groupInstancePosition;
	private Long attributeId;
	private String attributeName;
	private String attributeLabel;
	private String dataType;
	private boolean attributeRequired;
	private short attributePosition;
	private String value;
}
