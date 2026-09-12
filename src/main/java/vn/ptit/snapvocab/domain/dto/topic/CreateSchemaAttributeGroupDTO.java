package vn.ptit.snapvocab.domain.dto.topic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
public class CreateSchemaAttributeGroupDTO {

	private boolean multiple;

	@NotBlank(message = "Group name must not be blank")
	@Size(max = 255, message = "Group name must not exceed 255 characters")
	@Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Group name must contain only letters, numbers, and underscores")
	private String name;

	@Min(value = 0, message = "Group position must be greater than or equal to 0")
	private short position;

	@Size(max = 255, message = "Group label must not exceed 255 characters")
	private String label;

	@NotEmpty(message = "Group attributes must not be empty")
	@Valid
	private List<CreateSchemaAttributeDTO> attributes;

	public boolean isMultiple() {
		return multiple;
	}

	@JsonIgnore
	@AssertTrue(message = "Attribute names within a group must be unique")
	public boolean isAttributeNamesUnique() {
		if (attributes == null) {
			return true;
		}
		Set<String> names = new HashSet<>();
		return attributes.stream()
				.map(CreateSchemaAttributeDTO::getName)
				.filter(Objects::nonNull)
				.allMatch(name -> names.add(name.trim().toLowerCase()));
	}
}
