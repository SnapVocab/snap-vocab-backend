package vn.ptit.snapvocab.domain.dto.word;

import jakarta.validation.constraints.Size;
import java.util.Set;

public record BatchLookupRequest(
		@Size(min = 1, max = 100)
		Set<String> words
) {

}
