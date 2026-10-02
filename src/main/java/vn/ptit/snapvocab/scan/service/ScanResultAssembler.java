package vn.ptit.snapvocab.scan.service;

import vn.ptit.snapvocab.dictionary.service.WordService;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanItemDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.BatchLookupRequest;
import vn.ptit.snapvocab.dictionary.domain.dto.LookupResultDTO;

/**
 * Turns the AI's boxes into what the app shows: drops boxes below the
 * configured reliability, then attaches each label's dictionary entry.
 */
@Component
@RequiredArgsConstructor
public class ScanResultAssembler {

	private static final List<String> RELIABILITY_ORDER = List.of("LOW", "MEDIUM", "HIGH");

	private final WordService wordService;
	private final ScanProperties scanProperties;

	/** Backend-side guard on top of the AI's own filtering (BF-06 rule 2). */
	public List<DetectionDTO> filter(List<DetectionDTO> detections) {
		int min = rank(scanProperties.getMinReliability());
		return detections.stream().filter(d -> rank(d.reliability()) >= min).toList();
	}

	public ScanResultDTO assemble(ScanDetectionsDTO detections) {
		Map<String, LookupResultDTO> byWord = detections.detections().isEmpty()
				? Map.of()
				: lookupWords(detections.detections());

		List<ScanItemDTO> items = detections.detections().stream()
				.map(d -> toScanItem(d, findWord(d, byWord)))
				.toList();

		return ScanResultDTO.builder()
				.imageWidth(detections.imageWidth())
				.imageHeight(detections.imageHeight())
				.items(items)
				.build();
	}

	/** Unknown values rank lowest, so a typo in config shows everything rather than nothing. */
	private static int rank(String reliability) {
		return Math.max(0, RELIABILITY_ORDER.indexOf(reliability == null ? "" : reliability.toUpperCase(Locale.ROOT)));
	}

	/** Looks up every label and its headword in one query. */
	private Map<String, LookupResultDTO> lookupWords(List<DetectionDTO> detections) {
		Set<String> words = new HashSet<>();
		for (DetectionDTO d : detections) {
			words.add(d.label().toLowerCase(Locale.ROOT));
			if (d.headword() != null) {
				words.add(d.headword().toLowerCase(Locale.ROOT));
			}
		}
		return wordService.getWordsByEnglish(new BatchLookupRequest(words)).stream()
				.collect(Collectors.toMap(
						r -> r.getWord().toLowerCase(Locale.ROOT), r -> r, (first, duplicate) -> first));
	}

	/** Exact label first ("coffee mug"), then its headword ("mug"); null when neither is in the dictionary. */
	private LookupResultDTO findWord(DetectionDTO detection, Map<String, LookupResultDTO> byWord) {
		LookupResultDTO exact = byWord.get(detection.label().toLowerCase(Locale.ROOT));
		if (exact != null || detection.headword() == null) {
			return exact;
		}
		return byWord.get(detection.headword().toLowerCase(Locale.ROOT));
	}

	private ScanItemDTO toScanItem(DetectionDTO detection, LookupResultDTO word) {
		return ScanItemDTO.builder()
				.label(detection.label())
				.score(detection.score())
				.source(detection.source())
				.reliability(detection.reliability())
				.box(detection.box())
				.word(word)
				.build();
	}
}

