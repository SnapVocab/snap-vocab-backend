package vn.ptit.snapvocab.leaderboard.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWindow;
import vn.ptit.snapvocab.leaderboard.repository.LeaderboardRepository;
import vn.ptit.snapvocab.xp.domain.XpSourceType;

/**
 * Computes, caches and refreshes leaderboard snapshots (leaderboard_backend.md §5.6). The ledger is aggregated at
 * most once per window per refresh cycle, never per request (NFR #10).
 */
@Slf4j
@Service
public class LeaderboardSnapshotService {

	/** LB-01: learning XP only; booster bonus is inside amount. MISSION_REWARD, BADGE_REWARD etc. never count. */
	public static final Set<XpSourceType> COUNTED_SOURCES = EnumSet.of(XpSourceType.FLASHCARD_REVIEW, XpSourceType.QUIZ);
	static final List<String> SOURCE_NAMES = COUNTED_SOURCES.stream().map(Enum::name).toList();
	/** How long a request waits for another instance's cold-start computation before giving up. */
	static final Duration COLD_WAIT = Duration.ofSeconds(10);

	private final LeaderboardRepository repository;
	private final LeaderboardSnapshotStore store;
	private final Clock clock;
	private final TransactionTemplate readTransaction;
	private final Duration refreshInterval;
	private final MeterRegistry meterRegistry;
	private final Counter refreshFailed;

	public LeaderboardSnapshotService(LeaderboardRepository repository, LeaderboardSnapshotStore store, Clock clock,
			PlatformTransactionManager transactionManager, ObjectProvider<MeterRegistry> meterRegistry,
			@Value("${leaderboard.refresh-interval-ms:60000}") long refreshIntervalMs) {
		this.repository = repository;
		this.store = store;
		this.clock = clock;
		// Q1–Q3 must see one state of the ledger (§5.3).
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setReadOnly(true);
		this.readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.refreshInterval = Duration.ofMillis(refreshIntervalMs);
		this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
		// Exported as leaderboard_refresh_failed_total (§10.1); alert on any increase.
		this.refreshFailed = Counter.builder("leaderboard.refresh.failed")
				.description("Leaderboard refresh job runs that threw")
				.register(this.meterRegistry);
	}

	/** The snapshot for a window; computes it under the lock when it is missing or a closed one can now be final. */
	public LeaderboardSnapshot get(LeaderboardWindow window) {
		LeaderboardSnapshot cached = store.get(window.key());
		if (cached != null && !finalizable(window, cached)) {
			return cached;
		}
		LeaderboardSnapshot fresh = store.withLock(COLD_WAIT, () -> {
			LeaderboardSnapshot again = store.get(window.key());
			if (again != null && !finalizable(window, again)) {
				return again; // the job or another request got here first
			}
			return computeAndStore(window, "request");
		});
		if (fresh != null) {
			return fresh;
		}
		if (cached != null) {
			return cached; // lock busy for 10 s: an older snapshot beats an error
		}
		throw new IllegalStateException("Leaderboard snapshot unavailable: " + window.key());
	}

	/** A closed window whose snapshot was taken inside the grace period: compute it once more. */
	private boolean finalizable(LeaderboardWindow window, LeaderboardSnapshot snapshot) {
		return !window.live() && !snapshot.complete()
				&& !clock.instant().isBefore(window.end().plus(LeaderboardWindow.GRACE));
	}

	/** Keeps the live snapshot at most one interval old and finalizes closed ones. One instance works per run. */
	@Scheduled(fixedDelayString = "${leaderboard.refresh-interval-ms:60000}",
			initialDelayString = "${leaderboard.refresh-interval-ms:60000}")
	public void refresh() {
		try {
			store.withLock(Duration.ZERO, () -> {
				Instant now = clock.instant();
				LeaderboardWindow live = LeaderboardWindow.live(now);
				LeaderboardSnapshot current = store.get(live.key());
				// Another instance may have refreshed moments ago.
				if (current == null || current.computedAt().isBefore(now.minus(refreshInterval.dividedBy(2)))) {
					computeAndStore(live, "job");
				}
				List<LeaderboardWindow> closed = new ArrayList<>();
				LeaderboardWindow.baseline(now).ifPresent(closed::add);
				closed.add(LeaderboardWindow.previousWeek(now));
				for (LeaderboardWindow window : closed) {
					LeaderboardSnapshot snapshot = store.get(window.key());
					if (snapshot == null || !snapshot.complete()) {
						computeAndStore(window, "job");
					}
				}
				return null;
			});
		} catch (RuntimeException e) {
			refreshFailed.increment();
			log.warn("Leaderboard refresh failed", e);
		}
	}

	private LeaderboardSnapshot computeAndStore(LeaderboardWindow window, String trigger) {
		Timer.Sample sample = Timer.start(meterRegistry);
		LeaderboardSnapshot snapshot = compute(window);
		store.put(window.key(), snapshot, window.ttl());
		sample.stop(meterRegistry.timer("leaderboard.refresh", "window", window.live() ? "live" : "closed",
				"trigger", trigger));
		log.info("Leaderboard {} computed: {} participants", window.key(), snapshot.entries().size());
		return snapshot;
	}

	LeaderboardSnapshot compute(LeaderboardWindow window) {
		Instant computedAt = clock.instant();
		return readTransaction.execute(status -> {
			Map<Long, Long> peak = repository.peakHourXp(window.start(), window.end(), SOURCE_NAMES).stream()
					.collect(Collectors.toMap(LeaderboardRepository.PeakRow::getUserId,
							LeaderboardRepository.PeakRow::getPeakHourXp));
			List<LeaderboardSnapshot.Entry> unranked = repository
					.weeklyScores(window.start(), window.end(), SOURCE_NAMES).stream()
					.map(r -> new LeaderboardSnapshot.Entry(r.getUserId(), r.getScore(),
							Instant.EPOCH.plus(r.getReachedAtMicros(), ChronoUnit.MICROS), 0, false, r.getReviewXp(),
							r.getQuizXp(), r.getBonusXp(), r.getEvents().intValue(), r.getActiveDays().intValue(),
							peak.getOrDefault(r.getUserId(), 0L)))
					.toList();
			return new LeaderboardSnapshot(window.weekStart(), window.end(), window.live(), computedAt,
					window.completeAt(computedAt), repository.countEligibleLearners(), LeaderboardRanking.rank(unranked));
		});
	}
}
