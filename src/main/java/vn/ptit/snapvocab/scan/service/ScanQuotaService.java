package vn.ptit.snapvocab.scan.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.scan.exception.ScanException;

/**
 * Daily scan quota, counted from {@code scan_requests} rather than a separate
 * counter: a scan uses quota while it is PENDING/PROCESSING/DONE, and a FAILED
 * scan stops counting by itself, so there is no refund logic to get wrong.
 */
@Service
public class ScanQuotaService {

	private static final long LOCK_WAIT_SECONDS = 5;
	private static final long LOCK_LEASE_SECONDS = 15;

	private final ScanRequestRepository scanRequestRepository;
	private final UserRepository userRepository;
	private final RedissonClient redissonClient;
	private final TransactionTemplate transactionTemplate;
	private final ScanProperties properties;
	private final Clock clock;

	public ScanQuotaService(ScanRequestRepository scanRequestRepository, UserRepository userRepository,
			RedissonClient redissonClient, PlatformTransactionManager transactionManager,
			ScanProperties properties, Clock clock) {
		this.scanRequestRepository = scanRequestRepository;
		this.userRepository = userRepository;
		this.redissonClient = redissonClient;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.properties = properties;
		this.clock = clock;
	}

	public ScanQuotaDTO quota(Long userId) {
		int used = (int) countToday(userId);
		int limit = properties.getQuotaPerDay();
		return new ScanQuotaDTO(limit, used, Math.max(0, limit - used), startOfDay(1));
	}

	/**
	 * Creates the scan row if the user has quota left.
	 *
	 * <p>Count-then-insert is not atomic, so two simultaneous requests could both
	 * see 19/20. A per-user lock serialises them, and the insert commits before
	 * the lock is released so the next holder's count includes it.
	 *
	 * @throws ScanException QUOTA_EXCEEDED, with {@link ScanQuotaDTO} as details
	 */
	public ScanRequest reserve(Long userId, String objectKey, ScanStatus initialStatus) {
		RLock lock = redissonClient.getLock("scan:quota-lock:" + userId);
		boolean locked;
		try {
			locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ScanException(ScanErrorCode.AI_UNAVAILABLE, "Interrupted waiting for quota lock", e);
		}
		if (!locked) {
			throw new ScanException(ScanErrorCode.AI_QUEUE_FULL, "Quota lock busy for user " + userId);
		}
		try {
			ScanRequest reserved = transactionTemplate.execute(status -> {
				if (quota(userId).remaining() <= 0) {
					// Recorded for the Admin's "hết lượt" count (admin_overview_backend.md G1). FAILED never counts
					// toward quota, so this row costs the user nothing.
					scanRequestRepository.save(ScanRequest.builder()
							.id(UUID.randomUUID().toString())
							.user(userRepository.getReferenceById(userId))
							.status(ScanStatus.FAILED)
							.errorCode(ScanErrorCode.QUOTA_EXCEEDED)
							.errorMessage("Daily scan quota used up")
							.finishedAt(clock.instant())
							.build());
					return null;
				}
				return scanRequestRepository.save(ScanRequest.builder()
						.id(UUID.randomUUID().toString())
						.user(userRepository.getReferenceById(userId))
						.objectKey(objectKey)
						.status(initialStatus)
						.startedAt(initialStatus == ScanStatus.PROCESSING ? clock.instant() : null)
						.build());
			});
			if (reserved == null) {
				throw new ScanException(ScanErrorCode.QUOTA_EXCEEDED,
						"Daily scan quota used up for user " + userId, quota(userId), null);
			}
			return reserved;
		} finally {
			lock.unlock();
		}
	}

	private long countToday(Long userId) {
		return scanRequestRepository.countUsedSince(
				userId, ScanStatus.COUNTED, startOfDay(0));
	}

	/** Midnight in the quota zone, {@code daysFromToday} days from today. */
	private Instant startOfDay(int daysFromToday) {
		ZoneId zone = ZoneId.of(properties.getZone());
		return LocalDate.now(clock.withZone(zone)).plusDays(daysFromToday).atStartOfDay(zone).toInstant();
	}
}

