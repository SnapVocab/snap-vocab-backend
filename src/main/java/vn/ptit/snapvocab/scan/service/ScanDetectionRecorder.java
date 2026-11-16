package vn.ptit.snapvocab.scan.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.scan.domain.ScanDetection;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.scan.repository.ScanDetectionRepository;

/**
 * Writes a DONE scan's labels to {@code scan_detections} (admin_overview_backend.md §2.5 G2). Used by the worker,
 * inside the DONE transaction, and by {@link ScanDetectionBackfill}.
 */
@Component
@RequiredArgsConstructor
public class ScanDetectionRecorder {

	private static final int MAX_LENGTH = 120;

	private final WordRepository wordRepository;
	private final ScanDetectionRepository scanDetectionRepository;

	/** Labels and headwords (lower case) that are in the dictionary now; one light query. */
	public Set<String> knownWords(List<DetectionDTO> detections) {
		Set<String> words = new HashSet<>();
		for (DetectionDTO d : detections) {
			words.add(lower(d.label()));
			if (d.headword() != null) {
				words.add(lower(d.headword()));
			}
		}
		return words.isEmpty() ? Set.of() : new HashSet<>(wordRepository.findExistingEnglish(words));
	}

	/** {@code scan} must be the DONE job: its created_at and user are copied onto every row. */
	public void save(ScanRequest scan, List<DetectionDTO> detections, Set<String> knownWords) {
		scanDetectionRepository.saveAll(detections.stream().map(d -> ScanDetection.builder()
				.scanRequestId(scan.getId())
				.userId(scan.getUser().getId())
				.createdAt(scan.getCreatedAt())
				.label(truncate(lower(d.label())))
				.headword(d.headword() == null ? null : truncate(lower(d.headword())))
				.reliability(d.reliability() == null ? "LOW" : d.reliability().toUpperCase(Locale.ROOT))
				// Same order as ScanResultAssembler.findWord: exact label, then headword.
				.hasWord(knownWords.contains(lower(d.label()))
						|| (d.headword() != null && knownWords.contains(lower(d.headword()))))
				.build()).toList());
	}

	private static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}

	private static String truncate(String s) {
		return s.length() <= MAX_LENGTH ? s : s.substring(0, MAX_LENGTH);
	}
}
