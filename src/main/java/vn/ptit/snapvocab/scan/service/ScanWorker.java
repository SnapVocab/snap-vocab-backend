package vn.ptit.snapvocab.scan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanWebhookPayloadDTO;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.scan.exception.ScanException;
import vn.ptit.snapvocab.storage.service.StorageService;

/**
 * Runs scans: PENDING -> PROCESSING -> DONE / FAILED.
 *
 * <p>
 * Each status change is its own short transaction; nothing is held open
 * while the AI service works. If the sweeper failed the job in the meantime,
 * the worker's late write loses (status check + optimistic lock) and the job
 * stays FAILED.
 */
@Slf4j
@Component
public class ScanWorker {

	private final ScanRequestRepository scanRequestRepository;
	private final StorageService storageService;
	private final AiDetectionService aiDetectionService;
	private final ScanResultAssembler resultAssembler;
	private final ScanDetectionRecorder detectionRecorder;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public ScanWorker(ScanRequestRepository scanRequestRepository, StorageService storageService,
			AiDetectionService aiDetectionService, ScanResultAssembler resultAssembler,
			ScanDetectionRecorder detectionRecorder, ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.scanRequestRepository = scanRequestRepository;
		this.storageService = storageService;
		this.aiDetectionService = aiDetectionService;
		this.resultAssembler = resultAssembler;
		this.detectionRecorder = detectionRecorder;
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * Queue entry point for a PENDING job whose image is in storage. Never throws.
	 */
	public void process(String requestId) {
		MDC.put("requestId", requestId);
		try {
			ScanRequest job = start(requestId);
			if (job == null) {
				return;
			}
			try {
				if (aiDetectionService.isAsyncEnabled()) {
					dispatchAsync(job);
				} else {
					runDetection(requestId, () -> detectFromStorage(job));
				}
			} catch (ScanException e) {
				// already recorded on the job; the app reads it by polling
			}
		} finally {
			MDC.remove("requestId");
		}
	}

	/**
	 * Legacy synchronous path: the job is already PROCESSING (reserved that way),
	 * and the caller gets the AI response directly.
	 *
	 * @throws ScanException after recording the failure on the job
	 */
	public AiDetectResponseDTO runSync(String requestId, ScanImage image) {
		MDC.put("requestId", requestId);
		try {
			return runDetection(requestId, () -> aiDetectionService.detect(image, requestId));
		} finally {
			MDC.remove("requestId");
		}
	}

	/**
	 * The queue refused the job: fail it so it neither lingers nor counts toward
	 * quota.
	 */
	public void markRejected(String requestId) {
		finish(requestId, ScanStatus.PENDING, r -> {
			r.setStatus(ScanStatus.FAILED);
			r.setErrorCode(ScanErrorCode.AI_QUEUE_FULL);
			r.setErrorMessage("Rejected: scan queue full");
		});
	}

	public void handleWebhook(ScanWebhookPayloadDTO payload) {
		String requestId = payload.requestId();
		MDC.put("requestId", requestId);
		try {
			if ("DONE".equalsIgnoreCase(payload.status())) {
				complete(requestId, payload.modelVersion(), payload.processingTimeMs(),
						payload.imageWidth(), payload.imageHeight(), payload.detections());
			} else {
				ScanErrorCode code = mapWebhookErrorCode(payload.errorCode());
				String msg = payload.errorMessage() != null ? payload.errorMessage() : "AI webhook reported failure";
				log.warn("Scan {} failed via webhook with {}: {}", requestId, code, msg);
				fail(requestId, code, msg);
			}
		} finally {
			MDC.remove("requestId");
		}
	}

	public void complete(String requestId, String modelVersion, long processingTimeMs,
			int imageWidth, int imageHeight, List<DetectionDTO> rawDetections) {
		try {
			ScanDetectionsDTO detections = new ScanDetectionsDTO(
					imageWidth, imageHeight,
					resultAssembler.filter(rawDetections != null ? rawDetections : List.of()));
			String json = objectMapper.writeValueAsString(detections);
			Set<String> knownWords = detectionRecorder.knownWords(detections.detections());
			finish(requestId, ScanStatus.PROCESSING, r -> {
				r.setStatus(ScanStatus.DONE);
				r.setResultJson(json);
				r.setDetectionCount(detections.detections().size());
				r.setModelVersion(modelVersion);
				r.setProcessingTimeMs(processingTimeMs);
				// Admin label stats; skipped with the rest when the sweeper finished the job first.
				detectionRecorder.save(r, detections.detections(), knownWords);
			});
		} catch (JsonProcessingException e) {
			log.error("Scan {} failed to serialize detections", requestId, e);
			fail(requestId, ScanErrorCode.AI_ERROR, e.getMessage());
			throw new ScanException(ScanErrorCode.AI_ERROR, "Scan " + requestId + " failed unexpectedly", e);
		}
	}

	private static ScanErrorCode mapWebhookErrorCode(String aiCode) {
		if ("INVALID_IMAGE".equals(aiCode)) {
			return ScanErrorCode.INVALID_IMAGE;
		}
		if ("MODEL_NOT_READY".equals(aiCode)) {
			return ScanErrorCode.AI_UNAVAILABLE;
		}
		if ("AI_TIMEOUT".equals(aiCode)) {
			return ScanErrorCode.AI_TIMEOUT;
		}
		return ScanErrorCode.AI_ERROR;
	}

	private AiDetectResponseDTO runDetection(String requestId, Supplier<AiDetectResponseDTO> detector) {
		try {
			AiDetectResponseDTO response = detector.get();
			complete(requestId, response.modelVersion(), response.processingTimeMs(),
					response.imageWidth(), response.imageHeight(), response.detections());
			return response;
		} catch (ScanException e) {
			log.warn("Scan {} failed with {}: {}", requestId, e.getCode(), e.getMessage());
			fail(requestId, e.getCode(), e.getMessage());
			throw e;
		} catch (RuntimeException e) {
			log.error("Scan {} failed unexpectedly", requestId, e);
			fail(requestId, ScanErrorCode.AI_ERROR, e.getMessage());
			throw new ScanException(ScanErrorCode.AI_ERROR, "Scan " + requestId + " failed unexpectedly", e);
		}
	}

	/**
	 * PENDING -> PROCESSING; null when the job is gone or no longer pending (e.g.
	 * swept).
	 */
	private ScanRequest start(String requestId) {
		return transactionTemplate.execute(status -> {
			ScanRequest job = scanRequestRepository.findById(requestId).orElse(null);
			if (job == null || job.getStatus() != ScanStatus.PENDING) {
				log.warn("Scan {} skipped: {}", requestId, job == null ? "not found" : job.getStatus());
				return null;
			}
			job.setStatus(ScanStatus.PROCESSING);
			job.setStartedAt(clock.instant());
			return scanRequestRepository.save(job);
		});
	}

	private void dispatchAsync(ScanRequest job) {
		String requestId = job.getId();
		try {
			aiDetectionService.submitByUrl(resolveDownloadUrl(job), requestId);
		} catch (ScanException e) {
			log.warn("Scan {} async dispatch failed with {}: {}", requestId, e.getCode(), e.getMessage());
			fail(requestId, e.getCode(), e.getMessage());
			throw e;
		} catch (RuntimeException e) {
			log.error("Scan {} async dispatch failed unexpectedly", requestId, e);
			fail(requestId, ScanErrorCode.AI_ERROR, e.getMessage());
			throw new ScanException(ScanErrorCode.AI_ERROR, "Scan " + requestId + " failed unexpectedly", e);
		}
	}

	private AiDetectResponseDTO detectFromStorage(ScanRequest job) {
		return aiDetectionService.detectByUrl(resolveDownloadUrl(job), job.getId());
	}

	private String resolveDownloadUrl(ScanRequest job) {
		String key = job.getObjectKey();
		if (key == null || ScanObjectKeys.contentTypeOf(key).isEmpty()) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Unsupported key " + key);
		}
		return storageService.createDownloadUrl(key);
	}

	private void fail(String requestId, ScanErrorCode code, String message) {
		finish(requestId, ScanStatus.PROCESSING, r -> {
			r.setStatus(ScanStatus.FAILED);
			r.setErrorCode(code);
			r.setErrorMessage(truncate(message));
		});
	}

	/**
	 * Moves the job out of {@code expected}, unless someone else (the sweeper)
	 * already did.
	 */
	private void finish(String requestId, ScanStatus expected, Consumer<ScanRequest> update) {
		try {
			transactionTemplate.executeWithoutResult(status -> {
				ScanRequest job = scanRequestRepository.findById(requestId).orElse(null);
				if (job == null || job.getStatus() != expected) {
					log.warn("Scan {} result dropped: already {}", requestId,
							job == null ? "deleted" : job.getStatus());
					return;
				}
				update.accept(job);
				job.setFinishedAt(clock.instant());
				scanRequestRepository.save(job);
			});
		} catch (ObjectOptimisticLockingFailureException e) {
			log.warn("Scan {} result dropped: the sweeper finished it first", requestId);
		}
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 1000 ? message : message.substring(0, 1000);
	}
}

