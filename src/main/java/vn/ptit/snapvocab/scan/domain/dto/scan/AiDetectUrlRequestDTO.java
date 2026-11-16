package vn.ptit.snapvocab.scan.domain.dto.scan;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiDetectUrlRequestDTO(
		@JsonProperty("image_url") String imageUrl
) {
}

