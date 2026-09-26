package vn.ptit.snapvocab.scan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.exception.ScanException;

class AiDetectionServiceImplTest {

	private static final String OK_BODY = """
			{"request_id":"req-1","model_version":"Florence-2-base/od+self","processing_time_ms":1234,
			 "detections":[{"label":"coffee mug","headword":"mug","score":0.9,"source":"od",
			   "reliability":"HIGH","box":{"x1":1,"y1":2,"x2":3,"y2":4}}],
			 "labels":["coffee mug"],"image_width":10,"image_height":10}
			""";

	private MockRestServiceServer server;
	private AiServiceProperties properties;
	private AiDetectionServiceImpl service;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
		server = MockRestServiceServer.bindTo(builder).build();
		properties = new AiServiceProperties();
		service = new AiDetectionServiceImpl(builder.build(), properties);
	}

	private ResponseActions expectDetect() {
		return server.expect(once(), requestTo("http://ai-service/api/v1/detect"))
				.andExpect(method(HttpMethod.POST));
	}

	@Test
	void detectParsesContractAndSendsRequestId() {
		expectDetect()
				.andExpect(header("X-Request-Id", "req-1"))
				.andExpect(headerDoesNotExist("X-Service-Token"))
				.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

		AiDetectResponseDTO response = service.detect(image(), "req-1");

		assertThat(response.modelVersion()).isEqualTo("Florence-2-base/od+self");
		assertThat(response.processingTimeMs()).isEqualTo(1234);
		assertThat(response.detections()).singleElement().satisfies(d -> {
			assertThat(d.headword()).isEqualTo("mug");
			assertThat(d.reliability()).isEqualTo("HIGH");
		});
		server.verify();
	}

	@Test
	void detectSendsServiceTokenWhenConfigured() {
		properties.setServiceToken("s3cret");
		expectDetect()
				.andExpect(header("X-Service-Token", "s3cret"))
				.andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

		service.detect(image(), "req-1");

		server.verify();
	}

	@Test
	void invalidImageFromAiIsInvalidImage() {
		respondError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_IMAGE");
		assertCode(ScanErrorCode.INVALID_IMAGE);
	}

	@Test
	void modelNotReadyIsAiUnavailable() {
		respondError(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_NOT_READY");
		assertCode(ScanErrorCode.AI_UNAVAILABLE);
	}

	@Test
	void modelErrorIsAiError() {
		respondError(HttpStatus.INTERNAL_SERVER_ERROR, "MODEL_ERROR");
		assertCode(ScanErrorCode.AI_ERROR);
	}

	@Test
	void tokenMismatchIsAiErrorNotTheUsersFault() {
		respondError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
		assertCode(ScanErrorCode.AI_ERROR);
	}

	@Test
	void nonJsonErrorFallsBackToStatus() {
		expectDetect().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.contentType(MediaType.TEXT_HTML).body("<html>Bad gateway</html>"));
		assertCode(ScanErrorCode.AI_UNAVAILABLE);
	}

	@Test
	void readTimeoutIsAiTimeout() {
		expectDetect().andRespond(request -> {
			throw new SocketTimeoutException("Read timed out");
		});
		assertCode(ScanErrorCode.AI_TIMEOUT);
	}

	@Test
	void connectTimeoutIsAiUnavailable() {
		expectDetect().andRespond(request -> {
			throw new SocketTimeoutException("Connect timed out");
		});
		assertCode(ScanErrorCode.AI_UNAVAILABLE);
	}

	@Test
	void connectionRefusedIsAiUnavailable() {
		expectDetect().andRespond(request -> {
			throw new ConnectException("Connection refused");
		});
		assertCode(ScanErrorCode.AI_UNAVAILABLE);
	}

	private void respondError(HttpStatus status, String code) {
		expectDetect().andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"code\":\"" + code + "\",\"message\":\"boom\"}}"));
	}

	private void assertCode(ScanErrorCode expected) {
		assertThatThrownBy(() -> service.detect(image(), "req-1"))
				.isInstanceOf(ScanException.class)
				.extracting(e -> ((ScanException) e).getCode())
				.isEqualTo(expected);
		server.verify();
	}

	private ScanImage image() {
		return new ScanImage(new byte[] {1, 2, 3}, "photo", "image/png");
	}
}

