package vn.ptit.snapvocab.scan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanWebhookPayloadDTO;
import vn.ptit.snapvocab.scan.service.ScanWebhookVerifier;
import vn.ptit.snapvocab.scan.service.ScanWorker;

@ExtendWith(MockitoExtension.class)
class ScanWebhookControllerTest {

	private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
	private static final long NOW_EPOCH = NOW.getEpochSecond();
	private static final String SECRET = "webhook-secret";

	@Mock
	private ScanWorker scanWorker;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AiServiceProperties properties = new AiServiceProperties();
		properties.setWebhookSecret(SECRET);
		ScanWebhookVerifier verifier = new ScanWebhookVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
		mockMvc = MockMvcBuilders.standaloneSetup(
				new ScanWebhookController(verifier, scanWorker, new ObjectMapper())).build();
	}

	@Test
	void receiveAcceptsValidSignatureAndDispatchesToWorker() throws Exception {
		String body = """
				{"request_id":"req-1","status":"DONE","model_version":"Florence-2-base/od+self",
				 "processing_time_ms":1500,"image_width":640,"image_height":480,
				 "detections":[{"label":"coffee mug","headword":"mug","score":0.9,"source":"od",
				   "reliability":"HIGH","box":{"x1":1,"y1":2,"x2":3,"y2":4}}]}
				""";
		String sig = ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, body);

		mockMvc.perform(post("/api/webhooks/scan")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Webhook-Timestamp", String.valueOf(NOW_EPOCH))
						.header("X-Webhook-Signature", sig)
						.content(body))
				.andExpect(status().isOk());

		ArgumentCaptor<ScanWebhookPayloadDTO> captor = ArgumentCaptor.forClass(ScanWebhookPayloadDTO.class);
		verify(scanWorker).handleWebhook(captor.capture());
		assertThat(captor.getValue().requestId()).isEqualTo("req-1");
		assertThat(captor.getValue().status()).isEqualTo("DONE");
		assertThat(captor.getValue().detections()).hasSize(1);
	}

	@Test
	void receiveRejectsMissingOrInvalidSignature() throws Exception {
		String body = "{\"request_id\":\"req-1\",\"status\":\"DONE\"}";

		mockMvc.perform(post("/api/webhooks/scan")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/webhooks/scan")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Webhook-Timestamp", String.valueOf(NOW_EPOCH))
						.header("X-Webhook-Signature", "deadbeef")
						.content(body))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(scanWorker);
	}

	@Test
	void receiveReturnsBadRequestForMalformedOrIncompletePayloadWithValidSignature() throws Exception {
		String malformed = "{not-json";
		mockMvc.perform(post("/api/webhooks/scan")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Webhook-Timestamp", String.valueOf(NOW_EPOCH))
						.header("X-Webhook-Signature", ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, malformed))
						.content(malformed))
				.andExpect(status().isBadRequest());

		String missingFields = "{\"status\":\"DONE\"}";
		mockMvc.perform(post("/api/webhooks/scan")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Webhook-Timestamp", String.valueOf(NOW_EPOCH))
						.header("X-Webhook-Signature", ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, missingFields))
						.content(missingFields))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(scanWorker);
		verify(scanWorker, org.mockito.Mockito.never()).handleWebhook(any());
	}
}

