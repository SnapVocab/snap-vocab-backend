package vn.ptit.snapvocab.domain.dto.topic;

import java.time.Instant;
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
	private Instant createdAt;
	private Instant updatedAt;

	@Builder.Default
	private List<TopicDTO> children = new ArrayList<>();

	public TopicDTO(Long id, String name, String translation, String description, Long parentId,
			String descriptionTranslation, Long activeTemplateId, String activeTemplateName,
			String activeTemplateCode, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.translation = translation;
		this.description = description;
		this.parentId = parentId;
		this.descriptionTranslation = descriptionTranslation;
		this.activeTemplateId = activeTemplateId;
		this.activeTemplateName = activeTemplateName;
		this.activeTemplateCode = activeTemplateCode;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.children = new ArrayList<>();
	}
}

