package vn.ptit.snapvocab.scan.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanHistoryItemDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanStatusDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanSubmitResponse;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.scan.service.ScanDetectedWords;
import vn.ptit.snapvocab.scan.service.ScanJobQueue;
import vn.ptit.snapvocab.scan.service.ScanObjectKeys;
import vn.ptit.snapvocab.scan.service.ScanQuotaService;
import vn.ptit.snapvocab.scan.service.ScanResultAssembler;
import vn.ptit.snapvocab.scan.service.ScanService;
import vn.ptit.snapvocab.scan.service.ScanWorker;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.scan.exception.ScanException;

/**
 * Scan-to-vocabulary (BF-06). The main flow is asynchronous: upload to storage,
 * submit (quota + queue), then poll. The multipart endpoint is kept,
 * synchronous,
 * until the app has moved over; it still goes through quota and records a
 * {@link ScanRequest} so it cannot be used to bypass the daily limit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanServiceImpl implements ScanService {

	private final ScanWorker scanWorker;
	private final ScanJobQueue scanJobQueue;
	private final ScanQuotaService scanQuotaService;
	private final ScanResultAssembler resultAssembler;
	private final ScanRequestRepository scanRequestRepository;
	private final UserRepository userRepository;
	private final StorageService storageService;
	private final AiServiceProperties aiServiceProperties;
	private final ObjectMapper objectMapper;

	@Override
	@Deprecated
	public ScanResultDTO scanImage(MultipartFile file) {
		ScanImage image = toScanImage(file);
		ScanRequest job = scanQuotaService.reserve(currentUserId(), null, ScanStatus.PROCESSING);

		AiDetectResponseDTO detected = scanWorker.runSync(job.getId(), image);

		ScanResultDTO result = resultAssembler.assemble(new ScanDetectionsDTO(
				detected.imageWidth(), detected.imageHeight(), resultAssembler.filter(detected.detections())));
		result.setAnnotatedImageBase64(detected.annotatedImageBase64());
		result.setAnnotatedImageMime(detected.annotatedImageMime());
		return result;
	}

	@Override
	public PresignedUploadResponse createUploadUrl(String contentType) {
		if (!ScanObjectKeys.isSupportedType(contentType)) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Unsupported content type: " + contentType);
		}
		String objectKey = ScanObjectKeys.newKey(currentUserId(), contentType);
		return storageService.createUploadUrl(objectKey, contentType.toLowerCase(Locale.ROOT));
	}

	@Override
	public ScanSubmitResponse submit(String objectKey) {
		Long userId = currentUserId();
		if (!ScanObjectKeys.belongsTo(objectKey, userId)) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Object key not issued to user " + userId);
		}
		// Checked before charging quota: a failed upload must not cost a scan.
		long size = storageService.sizeOf(objectKey)
				.orElseThrow(() -> new ScanException(ScanErrorCode.INVALID_IMAGE, "Not uploaded: " + objectKey));
		if (size == 0 || size > maxUploadBytes()) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Upload size " + size + " out of range");
		}

		ScanRequest job = scanQuotaService.reserve(userId, objectKey, ScanStatus.PENDING);
		try {
			scanJobQueue.submit(() -> scanWorker.process(job.getId()));
		} catch (RejectedExecutionException e) {
			// FAILED rows do not count toward quota, so this also gives the scan back.
			scanWorker.markRejected(job.getId());
			throw new ScanException(ScanErrorCode.AI_QUEUE_FULL, "Scan queue full, rejected " + job.getId(), e);
		}
		log.info("Scan {} queued for user {}", job.getId(), userId);
		return new ScanSubmitResponse(job.getId(), ScanStatus.PENDING);
	}

	@Override
	public ScanStatusDTO getStatus(String requestId) {
		ScanRequest job = scanRequestRepository.findByIdAndUser_Id(requestId, currentUserId())
				.orElseThrow(() -> new ResourceNotFoundException("Scan not found: " + requestId));

		return ScanStatusDTO.builder()
				.requestId(job.getId())
				.status(job.getStatus())
				.errorCode(job.getErrorCode())
				.createdAt(job.getCreatedAt())
				.finishedAt(job.getFinishedAt())
				.imageUrl(job.getStatus() == ScanStatus.DONE && job.getObjectKey() != null
						? storageService.createDownloadUrl(job.getObjectKey())
						: null)
				.result(job.getStatus() == ScanStatus.DONE ? readResult(job) : null)
				.build();
	}

	@Override
	public ScanQuotaDTO getQuota() {
		return scanQuotaService.quota(currentUserId());
	}

	static final int HISTORY_MAX_SIZE = 50;
	static final int HISTORY_PREVIEW_WORDS = 4;

	@Override
	public PaginationObject<List<ScanHistoryItemDTO>> getHistory(int page, int size) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), HISTORY_MAX_SIZE));
		Page<ScanRequest> scans = scanRequestRepository.findByUser_IdOrderByCreatedAtDesc(currentUserId(), pageable);
		List<ScanHistoryItemDTO> items = scans.getContent().stream().map(this::toHistoryItem).toList();
		return PaginationObject.<List<ScanHistoryItemDTO>>builder()
				.data(items)
				.meta(Meta.builder()
						.page((long) pageable.getPageNumber())
						.size((long) pageable.getPageSize())
						.total(scans.getTotalElements())
						.pages((long) scans.getTotalPages())
						.build())
				.build();
	}

	private ScanHistoryItemDTO toHistoryItem(ScanRequest scan) {
		boolean done = scan.getStatus() == ScanStatus.DONE;
		return new ScanHistoryItemDTO(
				scan.getId(),
				scan.getStatus(),
				scan.getErrorCode(),
				scan.getCreatedAt(),
				scan.getFinishedAt(),
				scan.getDetectionCount(),
				done ? previewWords(scan) : List.of(),
				done && scan.getObjectKey() != null ? storageService.createDownloadUrl(scan.getObjectKey()) : null);
	}

	/** A corrupt result must not break the whole list: that row just shows no words. */
	private static List<String> previewWords(ScanRequest scan) {
		if (scan.getResultJson() == null) {
			return List.of();
		}
		try {
			return ScanDetectedWords.of(scan.getResultJson()).stream()
					.map(word -> word.trim().toLowerCase(Locale.ROOT))
					.filter(word -> !word.isEmpty())
					.distinct()
					.limit(HISTORY_PREVIEW_WORDS)
					.toList();
		} catch (IllegalStateException corrupt) {
			log.warn("Scan {} has an unreadable result", scan.getId());
			return List.of();
		}
	}

	private ScanResultDTO readResult(ScanRequest job) {
		try {
			return resultAssembler.assemble(objectMapper.readValue(job.getResultJson(), ScanDetectionsDTO.class));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Corrupt result for scan " + job.getId(), e);
		}
	}

	/** Reject what the AI service would reject anyway, before charging quota. */
	private ScanImage toScanImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Empty upload");
		}
		String contentType = image.getContentType();
		if (!ScanObjectKeys.isSupportedType(contentType)) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Unsupported content type: " + contentType);
		}
		if (image.getSize() > maxUploadBytes()) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE,
					"Upload exceeds " + aiServiceProperties.getMaxUploadMb() + "MB");
		}
		try {
			String filename = image.getOriginalFilename() != null ? image.getOriginalFilename() : "photo";
			return new ScanImage(image.getBytes(), filename, contentType.toLowerCase(Locale.ROOT));
		} catch (IOException e) {
			throw new ScanException(ScanErrorCode.INVALID_IMAGE, "Failed to read uploaded image", e);
		}
	}

	private long maxUploadBytes() {
		return aiServiceProperties.getMaxUploadMb() * 1024L * 1024L;
	}

	private Long currentUserId() {
		return SecurityUtils.getCurrentUserId()
				.or(() -> SecurityUtils.getCurrentUserLogin()
						.flatMap(userRepository::findOneByEmailIgnoreCase)
						.map(User::getId))
				.orElseThrow(() -> new AccessDeniedException("Authentication required to scan"));
	}
}

