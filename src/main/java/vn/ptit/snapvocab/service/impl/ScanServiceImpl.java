package vn.ptit.snapvocab.service.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.config.AiServiceProperties;
import vn.ptit.snapvocab.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.domain.dto.scan.ScanItemDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.domain.dto.word.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.word.LookupResultDTO;
import vn.ptit.snapvocab.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.service.AiDetectionService;
import vn.ptit.snapvocab.service.ScanService;
import vn.ptit.snapvocab.service.WordService;
import vn.ptit.snapvocab.util.exception.ScanException;

/**
 * Orchestrates the "snap a photo -> flashcards" flow: forwards the photo to the AI
 * service for object detection, then enriches each detected label with its dictionary
 * entry via {@link WordService} so the app gets definitions/translations in one call.
 */
@Service
@RequiredArgsConstructor
public class ScanServiceImpl implements ScanService {

	private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final AiDetectionService aiDetectionService;
	private final WordService wordService;
	private final AiServiceProperties aiServiceProperties;

	@Override
	public ScanResultDTO scanImage(MultipartFile image) {
		AiDetectResponseDTO detected = aiDetectionService.detect(toScanImage(image), UUID.randomUUID().toString());

		Map<String, LookupResultDTO> byWord = detected.detections().isEmpty()
				? Map.of()
				: lookupWords(detected.detections());

		List<ScanItemDTO> items = detected.detections().stream()
				.map(d -> toScanItem(d, findWord(d, byWord)))
				.toList();

		return ScanResultDTO.builder()
				.annotatedImageBase64(detected.annotatedImageBase64())
				.annotatedImageMime(detected.annotatedImageMime())
				.imageWidth(detected.imageWidth())
				.imageHeight(detected.imageHeight())
				.items(items)
				.build();
	}

	/** Reject what the AI service would reject anyway, without spending a round trip on it. */
	private ScanImage toScanImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Empty upload");
		}
		String contentType = image.getContentType();
		if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Unsupported content type: " + contentType);
		}
		long maxBytes = aiServiceProperties.getMaxUploadMb() * 1024L * 1024L;
		if (image.getSize() > maxBytes) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Upload exceeds " + aiServiceProperties.getMaxUploadMb() + "MB");
		}
		try {
			String filename = image.getOriginalFilename() != null ? image.getOriginalFilename() : "photo";
			return new ScanImage(image.getBytes(), filename, contentType);
		} catch (IOException e) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Failed to read uploaded image", e);
		}
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
