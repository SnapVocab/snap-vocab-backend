package vn.ptit.snapvocab.scan.domain.dto.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Callback body posted by snap-vocab-ai to {@code POST /api/webhooks/scan}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanWebhookPayloadDTO(
		@JsonProperty("request_id") String requestId,
		String status,
		@JsonProperty("model_version") String modelVersion,
		@JsonProperty("processing_time_ms") long processingTimeMs,
		@JsonProperty("image_width") int imageWidth,
		@JsonProperty("image_height") int imageHeight,
		List<DetectionDTO> detections,
		@JsonProperty("error_code") String errorCode,
		@JsonProperty("error_message") String errorMessage
) {
}

