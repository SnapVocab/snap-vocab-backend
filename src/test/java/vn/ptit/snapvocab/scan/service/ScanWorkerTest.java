package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.BoundingBoxDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.scan.exception.ScanException;
import vn.ptit.snapvocab.storage.service.StorageService;

@ExtendWith(MockitoExtension.class)
class ScanWorkerTest {

	private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
	private static final String KEY = "scans/7/abc.png";

	@Mock
	private ScanRequestRepository repository;
	@Mock
	private StorageService storageService;
	@Mock
	private AiDetectionService aiDetectionService;
	@Mock
	private ScanResultAssembler resultAssembler;
	@Mock
	private ScanDetectionRecorder detectionRecorder;
	@Mock
	private PlatformTransactionManager transactionManager;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private ScanWorker worker;
	private ScanRequest job;

	@BeforeEach
	void setUp() {
		worker = new ScanWorker(repository, storageService, aiDetectionService, resultAssembler,
				detectionRecorder, objectMapper, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		job = ScanRequest.builder().id("req-1").objectKey(KEY).status(ScanStatus.PENDING).build();
		lenient().when(repository.findById("req-1")).thenAnswer(inv -> Optional.of(job));
		lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(resultAssembler.filter(anyList())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void processStoresBoxesAndModelInfoOnSuccess() throws Exception {
		when(storageService.createDownloadUrl(KEY)).thenReturn("https://storage/signed");
		when(aiDetectionService.detectByUrl("https://storage/signed", "req-1"))
				.thenReturn(response(detection("cup")));

		worker.process("req-1");

		assertThat(job.getStatus()).isEqualTo(ScanStatus.DONE);
		assertThat(job.getStartedAt()).isEqualTo(NOW);
		assertThat(job.getFinishedAt()).isEqualTo(NOW);
		assertThat(job.getDetectionCount()).isEqualTo(1);
		assertThat(job.getModelVersion()).isEqualTo("Florence-2-base/od+self");
		assertThat(job.getProcessingTimeMs()).isEqualTo(1500L);
		ScanDetectionsDTO stored = objectMapper.readValue(job.getResultJson(), ScanDetectionsDTO.class);
		assertThat(stored.imageWidth()).isEqualTo(640);
		assertThat(stored.detections()).extracting(DetectionDTO::label).containsExactly("cup");
		verify(detectionRecorder).save(eq(job), eq(stored.detections()), any()); // G2-01: with the DONE write
	}

	@Test
	void processSendsPresignedDownloadUrlWithoutDownloadingBytes() {
		when(storageService.createDownloadUrl(KEY)).thenReturn("https://storage/signed");
		when(aiDetectionService.detectByUrl("https://storage/signed", "req-1")).thenReturn(response());

		worker.process("req-1");

		verify(storageService).createDownloadUrl(KEY);
		verify(aiDetectionService).detectByUrl("https://storage/signed", "req-1");
		verify(storageService, never()).download(any());
	}

	@Test
	void processRecordsAiErrorCodeOnFailure() {
		when(storageService.createDownloadUrl(KEY)).thenReturn("https://storage/signed");
		when(aiDetectionService.detectByUrl("https://storage/signed", "req-1"))
				.thenThrow(new ScanException(ScanErrorCode.AI_TIMEOUT, "slow"));

		worker.process("req-1");

		assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(ScanErrorCode.AI_TIMEOUT);
		assertThat(job.getFinishedAt()).isEqualTo(NOW);
	}

	@Test
	void processTurnsUnexpectedErrorsIntoAiError() {
		when(storageService.createDownloadUrl(KEY)).thenThrow(new RuntimeException("storage down"));

		worker.process("req-1");

		assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(ScanErrorCode.AI_ERROR);
	}

	@Test
	void processSkipsJobsThatAreNoLongerPending() {
		job.setStatus(ScanStatus.FAILED);

		worker.process("req-1");

		verifyNoInteractions(aiDetectionService, storageService);
		verify(repository, never()).save(any());
	}

	@Test
	void lateResultDoesNotOverwriteASweptJob() {
		when(storageService.createDownloadUrl(KEY)).thenReturn("https://storage/signed");
		when(aiDetectionService.detectByUrl("https://storage/signed", "req-1")).thenAnswer(inv -> {
			job.setStatus(ScanStatus.FAILED); // the sweeper got there while the AI was running
			job.setErrorCode(ScanErrorCode.INTERRUPTED);
			return response(detection("cup"));
		});

		worker.process("req-1");

		assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(ScanErrorCode.INTERRUPTED);
		assertThat(job.getResultJson()).isNull();
		verify(detectionRecorder, never()).save(any(), anyList(), any()); // G2-02: no orphan labels
	}

	@Test
	void runSyncRecordsFailureThenRethrows() {
		job.setStatus(ScanStatus.PROCESSING);
		when(aiDetectionService.detect(any(), eq("req-1")))
				.thenThrow(new ScanException(ScanErrorCode.INVALID_IMAGE, "bad"));

		assertThatThrownBy(() -> worker.runSync("req-1", new ScanImage(new byte[] {1}, "a.png", "image/png")))
				.isInstanceOfSatisfying(ScanException.class,
						e -> assertThat(e.getCode()).isEqualTo(ScanErrorCode.INVALID_IMAGE));
		assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
	}

	@Test
	void markRejectedFailsAPendingJobAsQueueFull() {
		worker.markRejected("req-1");

		assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(ScanErrorCode.AI_QUEUE_FULL);
	}

	private static AiDetectResponseDTO response(DetectionDTO... detections) {
		return new AiDetectResponseDTO("req-1", "Florence-2-base/od+self", 1500,
				List.of(detections), List.of(), null, null, 640, 480);
	}

	private static DetectionDTO detection(String label) {
		return new DetectionDTO(label, label, 0.9, "od", "HIGH", new BoundingBoxDTO(1, 2, 3, 4));
	}
}

