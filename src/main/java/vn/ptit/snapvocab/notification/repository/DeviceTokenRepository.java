package vn.ptit.snapvocab.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.notification.domain.DeviceToken;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

	/** Owner before an {@link #upsert}, to tag the {@code reassigned} metric (notification.md §4.5). */
	@Query("SELECT d.user.id FROM DeviceToken d WHERE d.token = :token")
	Optional<Long> findOwnerId(@Param("token") String token);

	/**
	 * A5: insert, refresh, or move the token to {@code userId}, atomically on {@code uq_device_tokens_token}, so two
	 * concurrent registrations end in one row without a DataIntegrityViolationException retry.
	 * {@code platform} is {@link vn.ptit.snapvocab.notification.domain.enumeration.DevicePlatform#name()}.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO device_tokens (user_id, token, platform, created_at, last_seen_at)
			VALUES (:userId, :token, :platform, :now, :now)
			ON DUPLICATE KEY UPDATE user_id = :userId, platform = :platform, last_seen_at = :now
			""", nativeQuery = true)
	int upsert(@Param("userId") Long userId, @Param("token") String token, @Param("platform") String platform,
			@Param("now") Instant now);

	/** J1 B5: every device of the user gets the push. */
	@Query("SELECT d.token FROM DeviceToken d WHERE d.user.id = :userId")
	List<String> findTokensByUserId(@Param("userId") Long userId);

	/** A6: only the caller's own token; 0 when unknown or someone else's. */
	@Modifying
	@Query("DELETE FROM DeviceToken d WHERE d.token = :token AND d.user.id = :userId")
	int deleteOwned(@Param("token") String token, @Param("userId") Long userId);

	/** J2: tokens Expo answered {@code DeviceNotRegistered}. */
	@Modifying
	@Query("DELETE FROM DeviceToken d WHERE d.token IN :tokens")
	int deleteByTokens(@Param("tokens") Collection<String> tokens);

	/** J3: devices not registered for 60 days. */
	@Modifying
	@Query("DELETE FROM DeviceToken d WHERE d.lastSeenAt < :cutoff")
	int deleteNotSeenSince(@Param("cutoff") Instant cutoff);
}
