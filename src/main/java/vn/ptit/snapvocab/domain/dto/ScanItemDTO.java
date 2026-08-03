package vn.ptit.snapvocab.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	private BoundingBoxDTO box;
	private String imageBase64;
	private LookupResultDTO word;
}
