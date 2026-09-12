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
import vn.ptit.snapvocab.domain.dto.ScanResultDTO;
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
	public ScanResultDTO scanImage(MultipartFile image) {
		AiDetectResponseDTO detected = aiDetectionService.detect(image);

		Map<String, LookupResultDTO> byWord = detected.detections().isEmpty()
				? Map.of()
				: lookupWords(detected.labels());

		List<ScanItemDTO> items = detected.detections().stream()
				.map(d -> toScanItem(d, byWord.get(d.label().toLowerCase(Locale.ROOT))))
				.toList();

		return ScanResultDTO.builder()
				.annotatedImageBase64(detected.annotatedImageBase64())
				.annotatedImageMime(detected.annotatedImageMime())
				.imageWidth(detected.imageWidth())
				.imageHeight(detected.imageHeight())
				.items(items)
				.build();
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
				.word(word)
				.build();
	}

	@Override
	public LiteScanResponseDTO scanImageLite(MultipartFile image) {
		AiLiteDetectResponseDTO detected = aiDetectionService.detectLite(image);

		// Labels come back with inconsistent casing/spacing ("Dog", " dog ", "DOG"),
		// so normalise first and treat those as one word.
		List<LiteScanDetectionDTO> detections = detected.detections().stream()
				.map(d -> new LiteScanDetectionDTO(normalize(d.label()), d.source(), d.box()))
				.toList();

		// Ordered by first appearance, so the client shows words in the order the
		// user's eye meets them in the photo.
		List<String> labels = detections.stream()
				.map(LiteScanDetectionDTO::label)
				.distinct()
				.toList();

		Map<String, LookupResultDTO> byWord = labels.isEmpty()
				? Map.of()
				: lookupWords(labels);

		List<ScanWordDTO> words = labels.stream()
				.map(label -> {
					LookupResultDTO found = byWord.get(label);
					return found != null ? ScanWordDTO.from(label, found) : ScanWordDTO.missing(label);
				})
				.toList();

		return new LiteScanResponseDTO(
				detected.mode(), detected.image(), labels, words, detections);
	}

	private static String normalize(String label) {
		return label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
	}
}
