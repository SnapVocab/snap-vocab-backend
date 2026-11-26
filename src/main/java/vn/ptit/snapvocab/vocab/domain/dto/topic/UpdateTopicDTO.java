package vn.ptit.snapvocab.vocab.domain.dto.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /api/admin/topics/{id}: the same text fields and limits as creating a topic (CreateTopicDTO). */
public record UpdateTopicDTO(
		@NotBlank @Size(min = 2, max = 100) String name,
		@Size(max = 255) String translation,
		@Size(max = 5000) String description,
		@Size(max = 5000) String descriptionTranslation) {
}
