package vn.ptit.snapvocab.vocab.domain.dto.topic;

import vn.ptit.snapvocab.vocab.domain.Schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class CreateSchemaDTO {

	@NotBlank(message = "Schema name is required")
	@Size(min = 2, max = 100, message = "Schema name must be between 2 and 100 characters")
	private String name;

	private String code;

	private Boolean isSystem;

	private String description;

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

