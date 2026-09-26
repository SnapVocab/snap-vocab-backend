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
public class SchemaAttributeGroupDTO {

	private Long groupId;
	private String name;
	private String label;
	private boolean multiple;
	private short position;

	@Builder.Default
	private List<SchemaAttributeDTO> attributes = new ArrayList<>();
}

