package vn.ptit.snapvocab.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanStatusDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanSubmitResponse;
import vn.ptit.snapvocab.domain.dto.storage.PresignedUploadResponse;
import vn.ptit.snapvocab.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.service.ScanService;

@SuppressWarnings("deprecation") // also covers the legacy synchronous endpoint
@ExtendWith(MockitoExtension.class)
class ScanControllerTest {

	@Mock
	private ScanService scanService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ScanController(scanService)).build();
	}

	@Test
	void multipartPostStillUsesLegacySyncFlow() throws Exception {
		MockMultipartFile image = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
		when(scanService.scanImage(image)).thenReturn(ScanResultDTO.builder()
				.annotatedImageMime("image/webp")
				.imageWidth(100)
				.imageHeight(50)
				.items(List.of())
				.build());

		mockMvc.perform(multipart("/api/scan").file(image))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.annotatedImageMime").value("image/webp"))
				.andExpect(jsonPath("$.items").isArray());

		verify(scanService).scanImage(image);
	}

	@Test
	void jsonPostSubmitsAndReturnsAccepted() throws Exception {
		when(scanService.submit("scans/7/a.jpg")).thenReturn(new ScanSubmitResponse("req-1", ScanStatus.PENDING));

		mockMvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON)
						.content("{\"objectKey\":\"scans/7/a.jpg\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.requestId").value("req-1"))
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void submitWithoutObjectKeyIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/scan").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void uploadUrlPassesContentType() throws Exception {
		when(scanService.createUploadUrl("image/jpeg")).thenReturn(
				new PresignedUploadResponse("https://put", "scans/7/a.jpg", "image/jpeg", 1L));

		mockMvc.perform(post("/api/scan/upload-url").contentType(MediaType.APPLICATION_JSON)
						.content("{\"contentType\":\"image/jpeg\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.objectName").value("scans/7/a.jpg"));
	}

	@Test
	void pollReturnsStatus() throws Exception {
		when(scanService.getStatus("req-1")).thenReturn(ScanStatusDTO.builder()
				.requestId("req-1").status(ScanStatus.PROCESSING).build());

		mockMvc.perform(get("/api/scan/req-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PROCESSING"));
	}

	@Test
	void quotaIsNotMistakenForARequestId() throws Exception {
		when(scanService.getQuota()).thenReturn(new ScanQuotaDTO(20, 3, 17, Instant.parse("2026-09-19T17:00:00Z")));

		mockMvc.perform(get("/api/scan/quota"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remaining").value(17));
	}
}
