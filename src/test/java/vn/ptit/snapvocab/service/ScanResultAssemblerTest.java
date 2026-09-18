package vn.ptit.snapvocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import vn.ptit.snapvocab.config.ScanProperties;
import vn.ptit.snapvocab.domain.dto.scan.BoundingBoxDTO;
import vn.ptit.snapvocab.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanItemDTO;
import vn.ptit.snapvocab.domain.dto.word.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.word.LookupResultDTO;

@ExtendWith(MockitoExtension.class)
class ScanResultAssemblerTest {

	@Mock
	private WordService wordService;

	private ScanProperties properties;
	private ScanResultAssembler assembler;

	@BeforeEach
	void setUp() {
		properties = new ScanProperties();
		assembler = new ScanResultAssembler(wordService, properties);
	}

	@Test
	void multiWordLabelFallsBackToHeadword() {
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of(word("mug")));

		ScanItemDTO item = single(detection("coffee mug", "mug", "HIGH"));

		assertThat(item.getLabel()).isEqualTo("coffee mug");
		assertThat(item.getWord().getWord()).isEqualTo("mug");
		assertThat(item.getReliability()).isEqualTo("HIGH");
	}

	@Test
	void exactLabelWinsOverHeadword() {
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of(word("glass"), word("wine glass")));

		assertThat(single(detection("wine glass", "glass", "HIGH")).getWord().getWord()).isEqualTo("wine glass");
	}

	@Test
	void labelAndHeadwordAreLookedUpInOneQuery() {
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of());

		assembler.assemble(result(detection("coffee mug", "mug", "HIGH"), detection("bottle", "bottle", "HIGH")));

		ArgumentCaptor<BatchLookupRequest> lookup = ArgumentCaptor.forClass(BatchLookupRequest.class);
		verify(wordService).getWordsByEnglish(lookup.capture());
		assertThat(lookup.getValue().words()).containsExactlyInAnyOrder("coffee mug", "mug", "bottle");
	}

	@Test
	void unknownWordLeavesWordNull() {
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of());

		assertThat(single(detection("gizmo", "gizmo", "HIGH")).getWord()).isNull();
	}

	@Test
	void noDetectionsSkipsDictionaryLookup() {
		assertThat(assembler.assemble(result()).getItems()).isEmpty();
		verifyNoInteractions(wordService);
	}

	@Test
	void filterDropsBoxesBelowMinReliability() {
		properties.setMinReliability("MEDIUM");

		List<DetectionDTO> kept = assembler.filter(List.of(
				detection("cup", "cup", "HIGH"), detection("tray", "tray", "MEDIUM"), detection("fork", "fork", "LOW")));

		assertThat(kept).extracting(DetectionDTO::label).containsExactly("cup", "tray");
	}

	@Test
	void unknownMinReliabilityKeepsEverything() {
		properties.setMinReliability("typo");

		assertThat(assembler.filter(List.of(detection("fork", "fork", "LOW")))).hasSize(1);
	}

	private ScanItemDTO single(DetectionDTO detection) {
		return assembler.assemble(result(detection)).getItems().get(0);
	}

	private static ScanDetectionsDTO result(DetectionDTO... detections) {
		return new ScanDetectionsDTO(100, 100, List.of(detections));
	}

	private static DetectionDTO detection(String label, String headword, String reliability) {
		return new DetectionDTO(label, headword, 0.9, "od", reliability, new BoundingBoxDTO(1, 2, 3, 4));
	}

	private static LookupResultDTO word(String word) {
		return LookupResultDTO.builder().exists(true).word(word).build();
	}
}
