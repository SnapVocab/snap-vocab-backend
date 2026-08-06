package vn.ptit.snapvocab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.ptit.snapvocab.domain.dto.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.AiLiteDetectResponseDTO;

class AiDetectionServiceImplTest {

	private MockRestServiceServer server;
	private AiDetectionServiceImpl service;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
		server = MockRestServiceServer.bindTo(builder).build();
		service = new AiDetectionServiceImpl(builder.build());
	}

	@Test
	void detectCallsDefaultAiEndpoint() {
		server.expect(once(), requestTo("http://ai-service/api/v1/detect"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"detections\":[],\"labels\":[]}", MediaType.APPLICATION_JSON));

		AiDetectResponseDTO response = service.detect(image());

		assertThat(response.detections()).isEmpty();
		server.verify();
	}

	@Test
	void detectLiteCallsExplicitLiteAiEndpoint() {
		String json = """
				{"mode":"lite","image":{"width":100,"height":50},"labels":["dog"],"detections":[
				  {"label":"dog","source":"od","box":{"x":0.1,"y":0.2,"width":0.3,"height":0.4}}
				]}
				""";
		server.expect(once(), requestTo("http://ai-service/api/v1/detect/lite"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

		AiLiteDetectResponseDTO response = service.detectLite(image());

		assertThat(response.mode()).isEqualTo("lite");
		assertThat(response.image().width()).isEqualTo(100);
		assertThat(response.detections()).singleElement()
				.satisfies(detection -> assertThat(detection.source()).isEqualTo("od"));
		server.verify();
	}

	private MockMultipartFile image() {
		return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
	}
}
