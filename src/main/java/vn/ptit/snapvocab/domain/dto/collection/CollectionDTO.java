package vn.ptit.snapvocab.domain.dto.collection;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CollectionDTO {

	private Long id;
	private String name;
	private String translation;
	private CollectionType type;
	private Long ownerId;
	private Instant createdAt;
	private Instant updatedAt;
}
