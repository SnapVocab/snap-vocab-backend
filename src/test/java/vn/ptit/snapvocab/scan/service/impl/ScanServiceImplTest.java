package vn.ptit.snapvocab.scan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.BoundingBoxDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanStatusDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanSubmitResponse;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.scan.service.ScanJobQueue;
import vn.ptit.snapvocab.scan.service.ScanQuotaService;
import vn.ptit.snapvocab.scan.service.ScanResultAssembler;
import vn.ptit.snapvocab.scan.service.ScanWorker;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.scan.exception.ScanException;

@SuppressWarnings("deprecation") // also covers the legacy synchronous endpoint
@ExtendWith(MockitoExtension.class)
class ScanServiceImplTest {

	private static final Long USER_ID = 7L;
	private static final String KEY = "scans/7/abc.jpg";

	@Mock
	private ScanWorker scanWorker;
	@Mock
	private ScanJobQueue scanJobQueue;
	@Mock
	private ScanQuotaService scanQuotaService;
	@Mock
	private ScanResultAssembler resultAssembler;
	@Mock
	private ScanRequestRepository scanRequestRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private StorageService storageService;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private ScanServiceImpl service;

	@BeforeEach
	void setUp() {
		AiServiceProperties ai = new AiServiceProperties();
		ai.setMaxUploadMb(1);
		service = new ScanServiceImpl(scanWorker, scanJobQueue, scanQuotaService, resultAssembler,
				scanRequestRepository, userRepository, storageService, ai, objectMapper);
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("a@b.c").claim("userId", USER_ID).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void uploadUrlIsIssuedUnderTheCallersPrefix() {
		service.createUploadUrl("image/webp");

		verify(storageService).createUploadUrl(
				matches("scans/7/[0-9a-f-]{36}\\.webp"), eq("image/webp"));
	}

	@Test
	void uploadUrlRejectsUnsupportedTypes() {
		assertCode(() -> service.createUploadUrl("image/gif"), ScanErrorCode.INVALID_IMAGE);
		verifyNoInteractions(storageService);
	}

	@Test
	void submitQueuesJobAndReturnsPending() {
		when(storageService.sizeOf(KEY)).thenReturn(Optional.of(2048L));
		when(scanQuotaService.reserve(USER_ID, KEY, ScanStatus.PENDING)).thenReturn(job(ScanStatus.PENDING));

		ScanSubmitResponse response = service.submit(KEY);

		assertThat(response).isEqualTo(new ScanSubmitResponse("req-1", ScanStatus.PENDING));
		verify(scanJobQueue).submit(any());
	}

	@Test
	void submitRejectsAnotherUsersKeyWithoutChargingQuota() {
		assertCode(() -> service.submit("scans/8/abc.jpg"), ScanErrorCode.INVALID_IMAGE);
		verifyNoInteractions(scanQuotaService, scanJobQueue);
	}

	@Test
	void submitRejectsMissingUploadWithoutChargingQuota() {
		when(storageService.sizeOf(KEY)).thenReturn(Optional.empty());

		assertCode(() -> service.submit(KEY), ScanErrorCode.INVALID_IMAGE);
		verifyNoInteractions(scanQuotaService);
	}

	@Test
	void submitRejectsOversizedUploadWithoutChargingQuota() {
		when(storageService.sizeOf(KEY)).thenReturn(Optional.of(1024L * 1024 + 1));

		assertCode(() -> service.submit(KEY), ScanErrorCode.INVALID_IMAGE);
		verifyNoInteractions(scanQuotaService);
	}

	@Test
	void fullQueueFailsTheJobSoItIsNotCharged() {
		when(storageService.sizeOf(KEY)).thenReturn(Optional.of(2048L));
		when(scanQuotaService.reserve(USER_ID, KEY, ScanStatus.PENDING)).thenReturn(job(ScanStatus.PENDING));
		doThrow(new RejectedExecutionException("full")).when(scanJobQueue).submit(any());

		assertCode(() -> service.submit(KEY), ScanErrorCode.AI_QUEUE_FULL);
		verify(scanWorker).markRejected("req-1");
	}

	@Test
	void statusOfDoneScanAssemblesStoredResult() throws Exception {
		ScanRequest done = job(ScanStatus.DONE);
		ScanDetectionsDTO stored = new ScanDetectionsDTO(640, 480, List.of(detection()));
		done.setResultJson(objectMapper.writeValueAsString(stored));
		when(scanRequestRepository.findByIdAndUser_Id("req-1", USER_ID)).thenReturn(Optional.of(done));
		when(storageService.createDownloadUrl(KEY)).thenReturn("https://storage/signed");
		ScanResultDTO assembled = ScanResultDTO.builder().imageWidth(640).items(List.of()).build();
		when(resultAssembler.assemble(stored)).thenReturn(assembled);

		ScanStatusDTO status = service.getStatus("req-1");

		assertThat(status.getStatus()).isEqualTo(ScanStatus.DONE);
		assertThat(status.getImageUrl()).isEqualTo("https://storage/signed");
		assertThat(status.getResult()).isSameAs(assembled);
	}

	@Test
	void historyListsTheCallersScansWithAPreviewOfTheirWords() throws Exception {
		ScanRequest done = job(ScanStatus.DONE);
		done.setResultJson(objectMapper.writeValueAsString(new ScanDetectionsDTO(640, 480, List.of(
				detection(), new DetectionDTO("Mug", "mug", 0.8, "od", "HIGH", new BoundingBoxDTO(1, 2, 3, 4))))));
		done.setDetectionCount(2);
		ScanRequest failed = ScanRequest.builder().id("req-2").objectKey(KEY).status(ScanStatus.FAILED)
				.errorCode(ScanErrorCode.AI_TIMEOUT).build();
		ScanRequest corrupt = ScanRequest.builder().id("req-3").objectKey(KEY).status(ScanStatus.DONE)
				.resultJson("{not json").build();
		when(scanRequestRepository.findByUser_IdOrderByCreatedAtDesc(eq(USER_ID), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(done, failed, corrupt),
						org.springframework.data.domain.PageRequest.of(0, 20), 3));
		when(storageService.createDownloadUrl(KEY)).thenReturn("https://storage/signed");

		var history = service.getHistory(0, 20);

		assertThat(history.getMeta().getTotal()).isEqualTo(3L);
		assertThat(history.getData()).hasSize(3);
		var first = history.getData().get(0);
		assertThat(first.words()).containsExactly("cup", "mug");
		assertThat(first.imageUrl()).isEqualTo("https://storage/signed");
		assertThat(first.detectionCount()).isEqualTo(2);
		var second = history.getData().get(1);
		assertThat(second.errorCode()).isEqualTo(ScanErrorCode.AI_TIMEOUT);
		assertThat(second.imageUrl()).isNull();
		assertThat(second.words()).isEmpty();
		// An unreadable result keeps the row, without words.
		assertThat(history.getData().get(2).words()).isEmpty();
	}

	@Test
	void historyPageSizeIsCapped() {
		when(scanRequestRepository.findByUser_IdOrderByCreatedAtDesc(eq(USER_ID), any()))
				.thenReturn(org.springframework.data.domain.Page.empty());

		assertThat(service.getHistory(-1, 1000).getMeta().getSize()).isEqualTo(50L);
	}

	@Test
	void statusOfFailedScanCarriesErrorCodeAndNoResult() {
		ScanRequest failed = job(ScanStatus.FAILED);
		failed.setErrorCode(ScanErrorCode.AI_TIMEOUT);
		when(scanRequestRepository.findByIdAndUser_Id("req-1", USER_ID)).thenReturn(Optional.of(failed));

		ScanStatusDTO status = service.getStatus("req-1");

		assertThat(status.getErrorCode()).isEqualTo(ScanErrorCode.AI_TIMEOUT);
		assertThat(status.getResult()).isNull();
		verifyNoInteractions(resultAssembler);
	}

	@Test
	void statusOfSomeoneElsesScanIsNotFound() {
		when(scanRequestRepository.findByIdAndUser_Id("req-1", USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getStatus("req-1")).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void syncScanChargesQuotaAndKeepsAnnotatedImage() {
		when(scanQuotaService.reserve(USER_ID, null, ScanStatus.PROCESSING)).thenReturn(job(ScanStatus.PROCESSING));
		when(scanWorker.runSync(eq("req-1"), any(ScanImage.class))).thenReturn(new AiDetectResponseDTO(
				"req-1", "v", 10, List.of(detection()), List.of(), "QUJD", "image/webp", 640, 480));
		when(resultAssembler.filter(any())).thenAnswer(inv -> inv.getArgument(0));
		when(resultAssembler.assemble(any())).thenReturn(ScanResultDTO.builder().items(List.of()).build());

		ScanResultDTO result = service.scanImage(new MockMultipartFile("file", "p.png", "image/PNG", new byte[] {1}));

		assertThat(result.getAnnotatedImageBase64()).isEqualTo("QUJD");
		verify(scanWorker).runSync(eq("req-1"),
				argThat(i -> i.contentType().equals("image/png")));
	}

	@Test
	void syncScanRejectsBadUploadBeforeChargingQuota() {
		assertCode(() -> service.scanImage(new MockMultipartFile("file", "a.gif", "image/gif", new byte[] {1})),
				ScanErrorCode.INVALID_IMAGE);
		assertCode(() -> service.scanImage(new MockMultipartFile("file", "a.png", "image/png", new byte[0])),
				ScanErrorCode.INVALID_IMAGE);
		verifyNoInteractions(scanQuotaService, scanWorker);
	}

	private static void assertCode(ThrowingCallable call, ScanErrorCode code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ScanException.class,
				e -> assertThat(e.getCode()).isEqualTo(code));
	}

	private static ScanRequest job(ScanStatus status) {
		return ScanRequest.builder().id("req-1").objectKey(KEY).status(status).build();
	}

	private static DetectionDTO detection() {
		return new DetectionDTO("cup", "cup", 0.9, "od", "HIGH", new BoundingBoxDTO(1, 2, 3, 4));
	}
}

