package vn.ptit.snapvocab.vocab.domain.dto.collection;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateCollectionDTO {

	@NotNull(message = "Collection name is required")
	@Size(min = 2, max = 100, message = "Collection name must be between 2 and 100 characters")
	private String name;

	private String translation;

	private CollectionType type;
}

