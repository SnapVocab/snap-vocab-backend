package vn.ptit.snapvocab.leaderboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot;

/**
 * The module's only boundary with Redis (leaderboard_backend.md §5.5). Snapshots are JSON strings, not
 * JsonJacksonCodec values, so renaming a class never makes a stored snapshot unreadable (KT-02).
 */
@Component
@RequiredArgsConstructor
public class LeaderboardSnapshotStore {

	private static final String LOCK_KEY = "leaderboard:v1:lock";
	/** Longer than any refresh; Redisson frees the lock after this even if the holder died. */
	private static final Duration LOCK_LEASE = Duration.ofSeconds(60);

	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	public LeaderboardSnapshot get(String key) {
		String json = redissonClient.<String>getBucket(key, StringCodec.INSTANCE).get();
		return json == null ? null : read(json);
	}

	public void put(String key, LeaderboardSnapshot snapshot, Duration ttl) {
		redissonClient.<String>getBucket(key, StringCodec.INSTANCE)
				.set(write(snapshot), ttl.toMillis(), TimeUnit.MILLISECONDS);
	}

	/** Runs {@code work} holding the cluster-wide refresh lock; returns null if the lock was not free within {@code wait}. */
	public <T> T withLock(Duration wait, Supplier<T> work) {
		RLock lock = redissonClient.getLock(LOCK_KEY);
		try {
			if (!lock.tryLock(wait.toMillis(), LOCK_LEASE.toMillis(), TimeUnit.MILLISECONDS)) {
				return null;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		try {
			return work.get();
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	private LeaderboardSnapshot read(String json) {
		try {
			return objectMapper.readValue(json, LeaderboardSnapshot.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Unreadable leaderboard snapshot", e);
		}
	}

	private String write(LeaderboardSnapshot snapshot) {
		try {
			return objectMapper.writeValueAsString(snapshot);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialize leaderboard snapshot", e);
		}
	}
}
