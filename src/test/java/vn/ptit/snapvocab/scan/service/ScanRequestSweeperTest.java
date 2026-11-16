package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
import vn.ptit.snapvocab.scan.config.AiServiceProperties;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;

@ExtendWith(MockitoExtension.class)
class ScanRequestSweeperTest {

	private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

	@Mock
	private ScanRequestRepository repository;
	@Mock
	private PlatformTransactionManager transactionManager;

	private ScanRequestSweeper sweeper;

	@BeforeEach
	void setUp() {
		ScanProperties scan = new ScanProperties(); // queue 3, 1 worker, 30s margin
		AiServiceProperties ai = new AiServiceProperties();
		ai.setReadTimeoutMs(60_000);
		sweeper = new ScanRequestSweeper(repository, transactionManager, scan, ai, Clock.fixed(NOW, ZoneOffset.UTC));
		lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void thresholdsFollowTimeoutAndQueueDepth() {
		assertThat(sweeper.maxRunTime()).isEqualTo(Duration.ofSeconds(90));
		// three jobs ahead, each allowed the full 60s, plus margin
		assertThat(sweeper.maxQueueWait()).isEqualTo(Duration.ofSeconds(210));
	}

	@Test
	void staleJobsAreFailedAsInterrupted() {
		ScanRequest running = job("a", ScanStatus.PROCESSING);
		ScanRequest queued = job("b", ScanStatus.PENDING);
		when(repository.findByStatusAndStartedAtBefore(ScanStatus.PROCESSING, NOW.minusSeconds(90)))
				.thenReturn(List.of(running));
		when(repository.findByStatusAndCreatedAtBefore(ScanStatus.PENDING, NOW.minusSeconds(210)))
				.thenReturn(List.of(queued));

		sweeper.failStaleJobs();

		for (ScanRequest job : List.of(running, queued)) {
			assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
			assertThat(job.getErrorCode()).isEqualTo(ScanErrorCode.INTERRUPTED);
			assertThat(job.getFinishedAt()).isEqualTo(NOW);
		}
	}

	@Test
	void jobThatFinishedMeanwhileIsLeftAlone() {
		ScanRequest done = job("a", ScanStatus.PROCESSING);
		when(repository.findByStatusAndStartedAtBefore(any(), any())).thenReturn(List.of(done));
		when(repository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());
		done.setStatus(ScanStatus.DONE); // reloaded inside the transaction as DONE

		sweeper.failStaleJobs();

		assertThat(done.getStatus()).isEqualTo(ScanStatus.DONE);
		verify(repository, never()).save(any());
	}

	@Test
	void unfinishedJobsAreFailedOnStartup() {
		ScanRequest lost = job("a", ScanStatus.PENDING);
		when(repository.findByStatusIn(ScanStatus.ACTIVE)).thenReturn(List.of(lost));

		sweeper.failJobsLostOnRestart();

		assertThat(lost.getStatus()).isEqualTo(ScanStatus.FAILED);
		assertThat(lost.getErrorCode()).isEqualTo(ScanErrorCode.INTERRUPTED);
	}

	@Test
	void processingJobsSurviveRestartWhenAsyncWebhookEnabled() {
		AiServiceProperties asyncProps = new AiServiceProperties();
		asyncProps.setCallbackUrl("https://backend/api/webhooks/scan");
		ScanRequestSweeper asyncSweeper = new ScanRequestSweeper(
				repository, transactionManager, new ScanProperties(), asyncProps, Clock.fixed(NOW, ZoneOffset.UTC));
		ScanRequest pending = job("p", ScanStatus.PENDING);
		when(repository.findByStatusIn(List.of(ScanStatus.PENDING))).thenReturn(List.of(pending));

		asyncSweeper.failJobsLostOnRestart();

		verify(repository).findByStatusIn(List.of(ScanStatus.PENDING));
		assertThat(pending.getStatus()).isEqualTo(ScanStatus.FAILED);
	}

	private ScanRequest job(String id, ScanStatus status) {
		ScanRequest job = ScanRequest.builder().id(id).status(status).build();
		lenient().when(repository.findById(id)).thenReturn(Optional.of(job));
		return job;
	}
}

