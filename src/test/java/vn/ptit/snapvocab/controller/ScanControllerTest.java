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
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;
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
		when(scanService.scanImage(image)).thenReturn(ScanResultDTO.builder()
				.annotatedImageBase64("QUJD")
				.annotatedImageMime("image/webp")
				.imageWidth(100)
				.imageHeight(50)
				.items(List.of())
				.build());

		mockMvc.perform(multipart("/api/scan").file(image))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.annotatedImageMime").value("image/webp"))
				.andExpect(jsonPath("$.imageWidth").value(100))
				.andExpect(jsonPath("$.items").isArray());

		verify(scanService).scanImage(image);
	}

	private MockMultipartFile image() {
		return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
	}
}
