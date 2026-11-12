package vn.ptit.snapvocab.leaderboard.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot;
import vn.ptit.snapvocab.leaderboard.service.LeaderboardSnapshotStore;

/** Test infrastructure for the leaderboard HTTP tests (leaderboard_backend.md §11.2). */
final class LeaderboardTestSupport {

	/** Wednesday 05/03/2025, 12:00 in Vietnam: a week no other test writes XP into. */
	static final Instant WEDNESDAY_NOON = Instant.parse("2025-03-05T05:00:00Z");

	private LeaderboardTestSupport() {
	}

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(WEDNESDAY_NOON);
		}

		@Bean
		@Primary
		InMemoryLeaderboardSnapshotStore inMemoryStore() {
			return new InMemoryLeaderboardSnapshotStore();
		}
	}

	/** Clock the test can move; every bean that injects Clock sees it. */
	static class MutableClock extends Clock {
		private volatile Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant instant) {
			now = instant;
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(now, zone);
		}
	}

	/** Redis stand-in: same contract as the real store, one JVM lock instead of the Redisson one. */
	static class InMemoryLeaderboardSnapshotStore extends LeaderboardSnapshotStore {
		private final Map<String, LeaderboardSnapshot> values = new ConcurrentHashMap<>();
		private final ReentrantLock lock = new ReentrantLock();

		InMemoryLeaderboardSnapshotStore() {
			super(null, null);
		}

		@Override
		public LeaderboardSnapshot get(String key) {
			return values.get(key);
		}

		@Override
		public void put(String key, LeaderboardSnapshot snapshot, Duration ttl) {
			values.put(key, snapshot);
		}

		@Override
		public <T> T withLock(Duration wait, Supplier<T> work) {
			try {
				if (!lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS)) {
					return null;
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
			try {
				return work.get();
			} finally {
				lock.unlock();
			}
		}

		void clear() {
			values.clear();
		}
	}
}
