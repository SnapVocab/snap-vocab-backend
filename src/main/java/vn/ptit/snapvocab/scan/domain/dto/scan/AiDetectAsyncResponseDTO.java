package vn.ptit.snapvocab.scan.domain.dto.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDetectAsyncResponseDTO(
		@JsonProperty("request_id") String requestId,
		String status
) {
}

