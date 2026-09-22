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
public class TopicDTO {

	private Long id;
	private String name;
	private String translation;
	private String description;
	private Long parentId;
	private String descriptionTranslation;
	private Long activeTemplateId;
	private String activeTemplateName;
	private String activeTemplateCode;
	private Boolean hasChildren;
	private Instant createdAt;
	private Instant updatedAt;
}
