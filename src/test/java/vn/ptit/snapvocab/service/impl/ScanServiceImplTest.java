package vn.ptit.snapvocab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.ptit.snapvocab.config.AiServiceProperties;
import vn.ptit.snapvocab.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.scan.BoundingBoxDTO;
import vn.ptit.snapvocab.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.domain.dto.scan.ScanItemDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.domain.dto.word.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.word.LookupResultDTO;
import vn.ptit.snapvocab.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.service.AiDetectionService;
import vn.ptit.snapvocab.service.WordService;
import vn.ptit.snapvocab.util.exception.ScanException;

@ExtendWith(MockitoExtension.class)
class ScanServiceImplTest {

	@Mock
	private AiDetectionService aiDetectionService;

	@Mock
	private WordService wordService;

	private ScanServiceImpl service;

	@BeforeEach
	void setUp() {
		AiServiceProperties properties = new AiServiceProperties();
		properties.setMaxUploadMb(1);
		service = new ScanServiceImpl(aiDetectionService, wordService, properties);
	}

	@Test
	void multiWordLabelFallsBackToHeadword() {
		givenDetections(detection("coffee mug", "mug"));
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of(word("mug")));

		ScanItemDTO item = service.scanImage(png()).getItems().get(0);

		assertThat(item.getLabel()).isEqualTo("coffee mug");
		assertThat(item.getWord().getWord()).isEqualTo("mug");
		assertThat(item.getReliability()).isEqualTo("HIGH");
	}

	@Test
	void exactLabelWinsOverHeadword() {
		givenDetections(detection("wine glass", "glass"));
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of(word("glass"), word("wine glass")));

		ScanItemDTO item = service.scanImage(png()).getItems().get(0);

		assertThat(item.getWord().getWord()).isEqualTo("wine glass");
	}

	@Test
	void labelAndHeadwordAreLookedUpInOneQuery() {
		givenDetections(detection("coffee mug", "mug"), detection("bottle", "bottle"));
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of());

		service.scanImage(png());

		ArgumentCaptor<BatchLookupRequest> lookup = ArgumentCaptor.forClass(BatchLookupRequest.class);
		verify(wordService).getWordsByEnglish(lookup.capture());
		assertThat(lookup.getValue().words()).containsExactlyInAnyOrder("coffee mug", "mug", "bottle");
	}

	@Test
	void unknownWordLeavesWordNull() {
		givenDetections(detection("gizmo", "gizmo"));
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of());

		ScanResultDTO result = service.scanImage(png());

		assertThat(result.getItems()).singleElement().extracting(ScanItemDTO::getWord).isNull();
	}

	@Test
	void noDetectionsSkipsDictionaryLookup() {
		givenDetections();

		assertThat(service.scanImage(png()).getItems()).isEmpty();
		verifyNoInteractions(wordService);
	}

	@Test
	void emptyUploadIsRejectedBeforeCallingAi() {
		assertInvalid(new MockMultipartFile("file", "photo.png", "image/png", new byte[0]));
	}

	@Test
	void unsupportedTypeIsRejectedBeforeCallingAi() {
		assertInvalid(new MockMultipartFile("file", "anim.gif", "image/gif", new byte[] {1}));
	}

	@Test
	void oversizedUploadIsRejectedBeforeCallingAi() {
		assertInvalid(new MockMultipartFile("file", "big.png", "image/png", new byte[1024 * 1024 + 1]));
	}

	private void assertInvalid(MockMultipartFile file) {
		assertThatThrownBy(() -> service.scanImage(file))
				.isInstanceOf(ScanException.class)
				.extracting(e -> ((ScanException) e).getCode())
				.isEqualTo(ScanErrorCode.INVALID_IMAGE);
		verifyNoInteractions(aiDetectionService);
	}

	private void givenDetections(DetectionDTO... detections) {
		when(aiDetectionService.detect(any(ScanImage.class), anyString())).thenReturn(new AiDetectResponseDTO(
				"req", "Florence-2-base/od+self", 10, List.of(detections),
				List.of(), null, null, 100, 100));
	}

	private static DetectionDTO detection(String label, String headword) {
		return new DetectionDTO(label, headword, 0.9, "od", "HIGH", new BoundingBoxDTO(1, 2, 3, 4));
	}

	private static LookupResultDTO word(String word) {
		return LookupResultDTO.builder().exists(true).word(word).build();
	}

	private static MockMultipartFile png() {
		return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
	}
}
