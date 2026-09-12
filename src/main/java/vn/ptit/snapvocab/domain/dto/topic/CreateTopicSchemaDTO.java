package vn.ptit.snapvocab.domain.dto.topic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
public class CreateTopicSchemaDTO {

	private Long topicId;

	@NotEmpty(message = "schemaGroups must not be empty")
	@Valid
	private List<CreateSchemaAttributeGroupDTO> schemaGroups;

	@JsonIgnore
	@AssertTrue(message = "Schema group names must be unique")
	public boolean isGroupNamesUnique() {
		if (schemaGroups == null) {
			return true;
		}
		Set<String> names = new HashSet<>();
		return schemaGroups.stream()
				.map(CreateSchemaAttributeGroupDTO::getName)
				.filter(Objects::nonNull)
				.allMatch(name -> names.add(name.trim().toLowerCase()));
	}
}
