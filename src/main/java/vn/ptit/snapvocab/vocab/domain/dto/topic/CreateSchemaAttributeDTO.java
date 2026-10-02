package vn.ptit.snapvocab.vocab.domain.dto.topic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class CreateSchemaAttributeDTO {

	@NotNull(message = "Attribute data type must not be null")
	private DataType dataType;

	@NotBlank(message = "Attribute name must not be blank")
	@Size(max = 255, message = "Attribute name must not exceed 255 characters")
	@Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Attribute name must contain only letters, numbers, and underscores")
	private String name;

	@Min(value = 0, message = "Attribute position must be greater than or equal to 0")
	private Integer position;

	private Boolean required;

	@Size(max = 255, message = "Attribute label must not exceed 255 characters")
	private String label;
}

