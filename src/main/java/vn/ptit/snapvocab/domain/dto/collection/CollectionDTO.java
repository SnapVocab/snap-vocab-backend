package vn.ptit.snapvocab.domain.dto.collection;

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
public class CollectionDTO {

	private Long id;
	private String name;
	private String translation;
}
