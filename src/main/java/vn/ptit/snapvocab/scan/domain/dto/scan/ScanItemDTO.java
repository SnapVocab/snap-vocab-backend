package vn.ptit.snapvocab.scan.domain.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.dictionary.domain.dto.LookupResultDTO;

/**
 * One detected object from a scanned photo, merged with its dictionary entry
 * ({@code word} is null when the detected label has no matching entry in the DB).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanItemDTO {

	private String label;
	private double score;
	private String source;
	/** HIGH / MEDIUM / LOW; show this to the user rather than {@code score}. */
	private String reliability;
	private BoundingBoxDTO box;
	private LookupResultDTO word;
}

