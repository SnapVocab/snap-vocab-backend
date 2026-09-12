package vn.ptit.snapvocab.domain.dto.topic;

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
public class SchemaAttributeDTO {

	private Long attributeId;
	private String name;
	private String label;
	private String dataType;
	private boolean required;
	private short position;
}
