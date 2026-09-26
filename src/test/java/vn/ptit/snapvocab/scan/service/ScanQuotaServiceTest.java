package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.scan.exception.ScanException;

@ExtendWith(MockitoExtension.class)
class ScanQuotaServiceTest {

	// 20:00 UTC = 03:00 next day in Vietnam (UTC+7): "today" there is 2026-09-19.
	private static final Instant NOW = Instant.parse("2026-09-18T20:00:00Z");
	private static final Instant VN_MIDNIGHT_TODAY = Instant.parse("2026-09-18T17:00:00Z");
	private static final Instant VN_MIDNIGHT_TOMORROW = Instant.parse("2026-09-19T17:00:00Z");

	@Mock
	private ScanRequestRepository scanRequestRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private RedissonClient redissonClient;
	@Mock
	private RLock lock;
	@Mock
	private PlatformTransactionManager transactionManager;

	private ScanProperties properties;
	private ScanQuotaService service;

	@BeforeEach
	void setUp() {
		properties = new ScanProperties();
		properties.setQuotaPerDay(3);
		service = new ScanQuotaService(scanRequestRepository, userRepository, redissonClient,
				transactionManager, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void quotaCountsFromLocalMidnightAndResetsAtNextOne() {
		givenUsedToday(2);

		ScanQuotaDTO quota = service.quota(7L);

		assertThat(quota).isEqualTo(new ScanQuotaDTO(3, 2, 1, VN_MIDNIGHT_TOMORROW));
	}

	@Test
	void remainingNeverGoesNegative() {
		givenUsedToday(5);

		assertThat(service.quota(7L).remaining()).isZero();
	}

	@Test
	void reserveCreatesRowUnderLockWhenQuotaLeft() throws Exception {
		givenLock(true);
		givenUsedToday(2);
		when(userRepository.getReferenceById(7L)).thenReturn(new User());
		when(scanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ScanRequest job = service.reserve(7L, "scans/7/a.jpg", ScanStatus.PENDING);

		assertThat(job.getId()).hasSize(36);
		assertThat(job.getStatus()).isEqualTo(ScanStatus.PENDING);
		assertThat(job.getObjectKey()).isEqualTo("scans/7/a.jpg");
		assertThat(job.getStartedAt()).isNull();
		verify(lock).unlock();
	}

	@Test
	void reserveForSyncScanStartsProcessingImmediately() throws Exception {
		givenLock(true);
		givenUsedToday(0);
		when(scanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ScanRequest job = service.reserve(7L, null, ScanStatus.PROCESSING);

		assertThat(job.getStartedAt()).isEqualTo(NOW);
	}

	@Test
	void reserveOverQuotaThrowsWithQuotaDetailsAndReleasesLock() throws Exception {
		givenLock(true);
		givenUsedToday(3);

		assertThatThrownBy(() -> service.reserve(7L, "scans/7/a.jpg", ScanStatus.PENDING))
				.isInstanceOfSatisfying(ScanException.class, e -> {
					assertThat(e.getCode()).isEqualTo(ScanErrorCode.QUOTA_EXCEEDED);
					assertThat(e.getDetails()).isEqualTo(new ScanQuotaDTO(3, 3, 0, VN_MIDNIGHT_TOMORROW));
				});
		verify(scanRequestRepository, never()).save(any());
		verify(lock).unlock();
	}

	@Test
	void busyLockIsRetryLaterNotQuotaExceeded() throws Exception {
		givenLock(false);

		assertThatThrownBy(() -> service.reserve(7L, "scans/7/a.jpg", ScanStatus.PENDING))
				.isInstanceOfSatisfying(ScanException.class,
						e -> assertThat(e.getCode()).isEqualTo(ScanErrorCode.AI_QUEUE_FULL));
		verify(lock, never()).unlock();
	}

	private void givenLock(boolean acquired) throws InterruptedException {
		when(redissonClient.getLock("scan:quota-lock:7")).thenReturn(lock);
		when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(acquired);
	}

	private void givenUsedToday(long used) {
		when(scanRequestRepository.countByUser_IdAndStatusInAndCreatedAtGreaterThanEqual(
				7L, ScanStatus.COUNTED, VN_MIDNIGHT_TODAY)).thenReturn(used);
	}
}

