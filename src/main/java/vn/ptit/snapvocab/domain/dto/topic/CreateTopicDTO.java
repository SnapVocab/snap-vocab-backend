package vn.ptit.snapvocab.domain.dto.topic;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateTopicDTO {

	@NotNull(message = "Topic name is required")
	@Size(min = 2, max = 100, message = "Topic name must be between 2 and 100 characters")
	private String name;
	private String translation;
	private String description;
	private Long parentId;
	private String descriptionTranslation;
}
