package vn.ptit.snapvocab.vocab.domain.dto.topic;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.vocab.domain.enumeration.DataType;

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
	private DataType dataType;
	private boolean attributeRequired;
	private short attributePosition;
	private String value;
}

