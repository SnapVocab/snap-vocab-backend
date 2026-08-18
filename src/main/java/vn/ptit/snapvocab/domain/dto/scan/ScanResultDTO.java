package vn.ptit.snapvocab.domain.dto.scan;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result of scanning one photo: the detected words plus a single annotated
 * image with every bounding box already drawn on it.
 *
 * <p>{@code imageWidth}/{@code imageHeight} describe the coordinate space shared
 * by the annotated image and every {@link ScanItemDTO#getBox() box}, so the
 * client can overlay them without rescaling.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanResultDTO {

	private String annotatedImageBase64;
	private String annotatedImageMime;
	private int imageWidth;
	private int imageHeight;
	private List<ScanItemDTO> items;
}
