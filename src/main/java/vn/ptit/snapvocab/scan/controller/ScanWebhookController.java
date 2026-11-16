package vn.ptit.snapvocab.scan.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanWebhookPayloadDTO;
import vn.ptit.snapvocab.scan.service.ScanWebhookVerifier;
import vn.ptit.snapvocab.scan.service.ScanWorker;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/scan")
@RequiredArgsConstructor
public class ScanWebhookController {

	private final ScanWebhookVerifier verifier;
	private final ScanWorker scanWorker;
	private final ObjectMapper objectMapper;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> receive(
			@RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
			@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
			@RequestBody(required = false) String rawBody) {
		if (!verifier.verify(timestamp, signature, rawBody)) {
			log.warn("Rejected scan webhook: invalid or expired signature");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		ScanWebhookPayloadDTO payload;
		try {
			payload = objectMapper.readValue(rawBody, ScanWebhookPayloadDTO.class);
		} catch (JsonProcessingException e) {
			return ResponseEntity.badRequest().build();
		}
		if (!StringUtils.hasText(payload.requestId()) || !StringUtils.hasText(payload.status())) {
			return ResponseEntity.badRequest().build();
		}
		scanWorker.handleWebhook(payload);
		return ResponseEntity.ok().build();
	}
}

