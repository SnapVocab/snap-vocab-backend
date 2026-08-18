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
public class TopicDTO {

	private Long id;
	private String name;
	private String translation;
	private String description;
	private Long parentId;
	private String descriptionTranslation;

	@Builder.Default
	private List<TopicDTO> children = new ArrayList<>();
}
