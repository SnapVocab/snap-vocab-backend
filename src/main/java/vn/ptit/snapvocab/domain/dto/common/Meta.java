package vn.ptit.snapvocab.domain.dto.common;

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
public class Meta {

	private Long page;
	private Long size;
	private Long total;
	private Long pages;
}
