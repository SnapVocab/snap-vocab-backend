package vn.ptit.snapvocab.scan.domain.dto.scan;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiDetectAsyncRequestDTO(
		@JsonProperty("request_id") String requestId,
		@JsonProperty("image_url") String imageUrl,
		@JsonProperty("callback_url") String callbackUrl
) {
}

