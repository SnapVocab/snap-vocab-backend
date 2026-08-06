package vn.ptit.snapvocab.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.domain.dto.ImageSizeDTO;
import vn.ptit.snapvocab.domain.dto.LiteScanDetectionDTO;
import vn.ptit.snapvocab.domain.dto.LiteScanResponseDTO;
import vn.ptit.snapvocab.domain.dto.NormalizedBoundingBoxDTO;
import vn.ptit.snapvocab.domain.dto.ScanWordDTO;
import vn.ptit.snapvocab.service.ScanService;

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
	void scanUsesFullScanFlow() throws Exception {
		MockMultipartFile image = image();
		when(scanService.scanImage(image)).thenReturn(List.of());

		mockMvc.perform(multipart("/api/scan").file(image))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		verify(scanService).scanImage(image);
	}

	@Test
	void scanLiteUsesLiteScanFlow() throws Exception {
		MockMultipartFile image = image();
		ScanWordDTO dog = new ScanWordDTO(
				true, "dog", "vi", "Tiếng Việt", List.of(), List.of(), List.of(), List.of());
		LiteScanDetectionDTO detection = new LiteScanDetectionDTO(
				"dog", "od", new NormalizedBoundingBoxDTO(0.1, 0.2, 0.3, 0.4));
		when(scanService.scanImageLite(image)).thenReturn(new LiteScanResponseDTO(
				"lite", new ImageSizeDTO(100, 50), List.of("dog"), List.of(dog), List.of(detection)));

		mockMvc.perform(multipart("/api/scan/lite").file(image))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("lite"))
				.andExpect(jsonPath("$.image.width").value(100))
				.andExpect(jsonPath("$.labels[0]").value("dog"))
				.andExpect(jsonPath("$.words.length()").value(1))
				.andExpect(jsonPath("$.words[0].word").value("dog"))
				.andExpect(jsonPath("$.detections.length()").value(1))
				.andExpect(jsonPath("$.detections[0].source").value("od"))
				.andExpect(jsonPath("$.detections[0].word").doesNotExist())
				.andExpect(jsonPath("$.detections[0].meanings").doesNotExist());

		verify(scanService).scanImageLite(image);
	}

	private MockMultipartFile image() {
		return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
	}
}
