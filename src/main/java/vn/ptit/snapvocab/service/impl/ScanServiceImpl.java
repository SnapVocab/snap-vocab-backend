package vn.ptit.snapvocab.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.DetectionDTO;
import vn.ptit.snapvocab.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.domain.dto.ScanItemDTO;
import vn.ptit.snapvocab.service.AiDetectionService;
import vn.ptit.snapvocab.service.ScanService;
import vn.ptit.snapvocab.service.WordService;

/**
 * Orchestrates the "snap a photo -> flashcards" flow: forwards the photo to the AI
 * service for object detection, then enriches each detected label with its dictionary
 * entry via {@link WordService} so the app gets definitions/translations in one call.
 */
@Service
@RequiredArgsConstructor
public class ScanServiceImpl implements ScanService {

	private final AiDetectionService aiDetectionService;
	private final WordService wordService;

	@Override
	public List<ScanItemDTO> scanImage(MultipartFile image) {
		AiDetectResponseDTO detected = aiDetectionService.detect(image);
		if (detected.detections().isEmpty()) {
			return List.of();
		}

		Map<String, LookupResultDTO> byWord = lookupWords(detected.labels());

		return detected.detections().stream()
				.map(d -> toScanItem(d, byWord.get(d.label().toLowerCase(Locale.ROOT))))
				.toList();
	}

	private Map<String, LookupResultDTO> lookupWords(List<String> labels) {
		Set<String> words = new HashSet<>(labels);
		return wordService.getWordsByEnglish(new BatchLookupRequest(words)).stream()
				.collect(Collectors.toMap(
						r -> r.getWord().toLowerCase(Locale.ROOT), r -> r, (first, duplicate) -> first));
	}

	private ScanItemDTO toScanItem(DetectionDTO detection, LookupResultDTO word) {
		return ScanItemDTO.builder()
				.label(detection.label())
				.score(detection.score())
				.source(detection.source())
				.box(detection.box())
				.imageBase64(detection.imageBase64())
				.word(word)
				.build();
	}
}
