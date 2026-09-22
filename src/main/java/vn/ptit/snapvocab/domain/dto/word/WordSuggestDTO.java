package vn.ptit.snapvocab.domain.dto.word;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class WordSuggestDTO {

	private Long id;
	private String word;
}
