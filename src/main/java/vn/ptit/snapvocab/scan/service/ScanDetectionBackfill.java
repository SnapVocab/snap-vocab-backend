package vn.ptit.snapvocab.scan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;

/**
 * One-off: writes scan_detections for DONE scans that finished before G2 (admin_overview_backend.md §2.5). Enabled by
 * {@code scan.detections-backfill: true}; skips scans that already have rows, so a second run adds nothing.
 */
@Slf4j
@Component
public class ScanDetectionBackfill implements ApplicationRunner {

	private final ScanRequestRepository scanRequestRepository;
	private final ScanDetectionRecorder recorder;
	private final ObjectMapper objectMapper;
	private final ScanProperties properties;
	private final TransactionTemplate transactionTemplate;

	public ScanDetectionBackfill(ScanRequestRepository scanRequestRepository, ScanDetectionRecorder recorder,
			ObjectMapper objectMapper, ScanProperties properties, PlatformTransactionManager transactionManager) {
		this.scanRequestRepository = scanRequestRepository;
		this.recorder = recorder;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public void run(ApplicationArguments args) {
		if (properties.isDetectionsBackfill()) {
			log.info("Scan detections backfill done: {} scans", backfill());
		}
	}

	/** @return scans processed (with or without labels) */
	public int backfill() {
		// Keyset cursor, not "WHERE NOT EXISTS" alone: a scan with zero labels never gets a row and would repeat.
		Instant afterCreated = Instant.EPOCH;
		String afterId = "";
		int total = 0;
		while (true) {
			Instant cursorCreated = afterCreated;
			String cursorId = afterId;
			List<ScanRequest> page = transactionTemplate.execute(status -> {
				List<ScanRequest> scans = scanRequestRepository.findDoneWithoutDetections(cursorCreated, cursorId);
				scans.forEach(this::record);
				return scans;
			});
			if (page == null || page.isEmpty()) {
				return total;
			}
			total += page.size();
			ScanRequest last = page.get(page.size() - 1);
			afterCreated = last.getCreatedAt();
			afterId = last.getId();
		}
	}

	private void record(ScanRequest scan) {
		if (scan.getResultJson() == null) {
			return;
		}
		try {
			ScanDetectionsDTO result = objectMapper.readValue(scan.getResultJson(), ScanDetectionsDTO.class);
			recorder.save(scan, result.detections(), recorder.knownWords(result.detections()));
		} catch (Exception e) {
			log.warn("Scan {} skipped by detections backfill: {}", scan.getId(), e.getMessage());
		}
	}
}
