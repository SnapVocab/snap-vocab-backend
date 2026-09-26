package vn.ptit.snapvocab.vocab.domain.dto.topic;

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
public class SchemaAttributeDTO {

	private Long attributeId;
	private String name;
	private String label;
	private DataType dataType;
	private boolean required;
	private short position;
}

