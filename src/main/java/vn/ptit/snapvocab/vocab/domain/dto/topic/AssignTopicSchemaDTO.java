package vn.ptit.snapvocab.vocab.domain.dto.topic;

import vn.ptit.snapvocab.vocab.domain.Schema;

import jakarta.validation.constraints.NotNull;
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
public class AssignTopicSchemaDTO {

	@NotNull(message = "Schema ID is required")
	private Long schemaId;
}

