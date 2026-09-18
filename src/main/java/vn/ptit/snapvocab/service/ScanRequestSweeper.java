package vn.ptit.snapvocab.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.config.AiServiceProperties;
import vn.ptit.snapvocab.config.ScanProperties;
import vn.ptit.snapvocab.domain.ScanRequest;
import vn.ptit.snapvocab.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.repository.ScanRequestRepository;

/**
 * Fails scans that can no longer finish, so the app stops polling and the
 * user gets the scan back (FAILED rows do not count toward quota).
 *
 * <p>Thresholds are derived from the real limits rather than guessed: a running
 * job cannot outlive the AI read timeout, and a queued job waits at most for
 * every job ahead of it to time out.
 */
@Slf4j
@Component
public class ScanRequestSweeper {

	private final ScanRequestRepository scanRequestRepository;
	private final TransactionTemplate transactionTemplate;
	private final ScanProperties scanProperties;
	private final AiServiceProperties aiServiceProperties;
	private final Clock clock;

	public ScanRequestSweeper(ScanRequestRepository scanRequestRepository,
			PlatformTransactionManager transactionManager, ScanProperties scanProperties,
			AiServiceProperties aiServiceProperties, Clock clock) {
		this.scanRequestRepository = scanRequestRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.scanProperties = scanProperties;
		this.aiServiceProperties = aiServiceProperties;
		this.clock = clock;
	}

	/** The queue is in memory, so after a restart every unfinished job is gone. Single-instance assumption. */
	@EventListener(ApplicationReadyEvent.class)
	public void failJobsLostOnRestart() {
		List<ScanRequest> lost = scanRequestRepository.findByStatusIn(ScanStatus.ACTIVE);
		lost.forEach(job -> failIfStillActive(job.getId(), "Lost on server restart"));
		if (!lost.isEmpty()) {
			log.warn("Marked {} unfinished scans INTERRUPTED after restart", lost.size());
		}
	}

	@Scheduled(fixedDelayString = "${scan.sweep-interval-ms:30000}", initialDelayString = "${scan.sweep-interval-ms:30000}")
	public void failStaleJobs() {
		Instant now = clock.instant();
		List<ScanRequest> stale = new ArrayList<>();
		stale.addAll(scanRequestRepository.findByStatusAndStartedAtBefore(
				ScanStatus.PROCESSING, now.minus(maxRunTime())));
		stale.addAll(scanRequestRepository.findByStatusAndCreatedAtBefore(
				ScanStatus.PENDING, now.minus(maxQueueWait())));
		stale.forEach(job -> failIfStillActive(job.getId(), "Exceeded maximum run time"));
		if (!stale.isEmpty()) {
			log.warn("Marked {} stale scans INTERRUPTED", stale.size());
		}
	}

	/** Longest a job can legitimately stay PROCESSING. */
	Duration maxRunTime() {
		return Duration.ofMillis(aiServiceProperties.getReadTimeoutMs())
				.plusSeconds(scanProperties.getStaleMarginSeconds());
	}

	/** Longest a job can legitimately stay PENDING: every job ahead of it runs to the timeout. */
	Duration maxQueueWait() {
		int rounds = (int) Math.ceil((double) scanProperties.getQueueCapacity() / Math.max(1, scanProperties.getWorkers()));
		return Duration.ofMillis(aiServiceProperties.getReadTimeoutMs()).multipliedBy(rounds)
				.plusSeconds(scanProperties.getStaleMarginSeconds());
	}

	private void failIfStillActive(String requestId, String reason) {
		try {
			transactionTemplate.executeWithoutResult(status -> scanRequestRepository.findById(requestId)
					.filter(job -> ScanStatus.ACTIVE.contains(job.getStatus()))
					.ifPresent(job -> {
						job.setStatus(ScanStatus.FAILED);
						job.setErrorCode(ScanErrorCode.INTERRUPTED);
						job.setErrorMessage(reason);
						job.setFinishedAt(clock.instant());
						scanRequestRepository.save(job);
					}));
		} catch (ObjectOptimisticLockingFailureException e) {
			log.info("Scan {} finished while being swept; keeping the worker's result", requestId);
		}
	}
}
