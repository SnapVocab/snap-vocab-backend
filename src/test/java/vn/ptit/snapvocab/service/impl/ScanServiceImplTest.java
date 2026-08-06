package vn.ptit.snapvocab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.ptit.snapvocab.domain.dto.AiLiteDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.ImageSizeDTO;
import vn.ptit.snapvocab.domain.dto.LiteDetectionDTO;
import vn.ptit.snapvocab.domain.dto.LiteScanResponseDTO;
import vn.ptit.snapvocab.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.domain.dto.NormalizedBoundingBoxDTO;
import vn.ptit.snapvocab.service.AiDetectionService;
import vn.ptit.snapvocab.service.WordService;

@ExtendWith(MockitoExtension.class)
class ScanServiceImplTest {

	@Mock
	private AiDetectionService aiDetectionService;

	@Mock
	private WordService wordService;

	@InjectMocks
	private ScanServiceImpl scanService;

	@Test
	void sixDogDetectionsUseOneBatchLookupAndKeepAllBoxes() {
		MockMultipartFile image = image();
		List<LiteDetectionDTO> detections = List.of(
				detection("Dog", "od", 0.01),
				detection(" dog ", "od", 0.11),
				detection("DOG", "od", 0.21),
				detection("dog", "dense", 0.31),
				detection("Dog", "od", 0.41),
				detection(" dog", "dense", 0.51));
		when(aiDetectionService.detectLite(image)).thenReturn(aiResponse(detections));
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of(foundWord("dog")));

		LiteScanResponseDTO response = scanService.scanImageLite(image);

		ArgumentCaptor<BatchLookupRequest> request = ArgumentCaptor.forClass(BatchLookupRequest.class);
		verify(wordService, times(1)).getWordsByEnglish(request.capture());
		assertThat(request.getValue().words()).containsExactly("dog");
		assertThat(response.labels()).containsExactly("dog");
		assertThat(response.words()).singleElement().satisfies(word -> {
			assertThat(word.exists()).isTrue();
			assertThat(word.word()).isEqualTo("dog");
		});
		assertThat(response.detections()).hasSize(6);
		assertThat(response.detections()).extracting(d -> d.box().x())
				.containsExactly(0.01, 0.11, 0.21, 0.31, 0.41, 0.51);
		assertThat(response.detections()).allSatisfy(d -> assertThat(d.label()).isEqualTo("dog"));
	}

	@Test
	void wordsFollowFirstDetectionOrderAndMissingWordAppearsOnce() {
		MockMultipartFile image = image();
		List<LiteDetectionDTO> detections = List.of(
				detection(" Crown ", "dense", 0.1),
				detection("DOG", "od", 0.2),
				detection("unknown-label", "dense", 0.3),
				detection("crown", "od", 0.4),
				detection(" UNKNOWN-LABEL ", "dense", 0.5));
		when(aiDetectionService.detectLite(image)).thenReturn(aiResponse(detections));
		// Deliberately return dictionary rows in a different order.
		when(wordService.getWordsByEnglish(any())).thenReturn(
				List.of(foundWord("dog"), foundWord("crown")));

		LiteScanResponseDTO response = scanService.scanImageLite(image);

		verify(wordService, times(1)).getWordsByEnglish(any());
		assertThat(response.labels()).containsExactly("crown", "dog", "unknown-label");
		assertThat(response.words()).extracting(word -> word.word())
				.containsExactly("crown", "dog", "unknown-label");
		assertThat(response.words().get(2).exists()).isFalse();
		assertThat(response.detections()).hasSize(5);
	}

	private AiLiteDetectResponseDTO aiResponse(List<LiteDetectionDTO> detections) {
		return new AiLiteDetectResponseDTO(
				"lite", new ImageSizeDTO(100, 50), List.of("ignored-upstream-labels"), detections);
	}

	private LiteDetectionDTO detection(String label, String source, double x) {
		return new LiteDetectionDTO(
				label, source, new NormalizedBoundingBoxDTO(x, 0.2, 0.03, 0.04));
	}

	private LookupResultDTO foundWord(String word) {
		return LookupResultDTO.builder()
				.exists(true)
				.word(word)
				.langCode("vi")
				.langName("Tiếng Việt")
				.pronunciations(List.of())
				.meanings(List.of())
				.translations(List.of())
				.relations(List.of())
				.build();
	}

	private MockMultipartFile image() {
		return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
	}
}
