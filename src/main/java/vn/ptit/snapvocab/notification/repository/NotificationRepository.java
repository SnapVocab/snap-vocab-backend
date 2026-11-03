package vn.ptit.snapvocab.notification.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.notification.domain.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * J1 B1 (notification.md §4.4): activated users whose reminder time (VN time of day) is in
	 * {@code [windowStart, windowEnd]} and who have no {@code SRS_REMINDER} for {@code todayVn} yet.
	 * Lower bound is inclusive, unlike the spec's {@code >}: with {@code >} a 00:00 reminder never fires once the
	 * window is clamped to 00:00; the extra tick it allows is absorbed by the dedupe key.
	 */
	@Query(value = """
			SELECT s.user_id AS userId, NULL AS uiLanguage, s.push_enabled AS pushEnabled,
			       u.first_name AS firstName
			FROM user_settings s
			JOIN users u ON u.id = s.user_id
			WHERE u.activated = TRUE
			  AND s.srs_reminder_at >= :windowStart
			  AND s.srs_reminder_at <= :windowEnd
			  AND NOT EXISTS (SELECT 1 FROM notifications n
			                  WHERE n.dedupe_key = CONCAT('SRS_REMINDER:', s.user_id, ':', :todayVn))
			""", nativeQuery = true)
	List<SrsReminderCandidate> findSrsReminderCandidates(@Param("windowStart") LocalTime windowStart,
			@Param("windowEnd") LocalTime windowEnd, @Param("todayVn") LocalDate todayVn);

	/**
	 * J4: activated users whose stored streak reaches {@code minStreak} and who have no {@code STREAK_WARNING} for
	 * {@code todayVn} yet. The stored streak may be stale (a broken chain settles lazily), so the job re-checks the
	 * streak and today's study in Java. No settings row = defaults (Vietnamese, push on).
	 */
	@Query(value = """
			SELECT u.id AS userId, NULL AS uiLanguage, s.push_enabled AS pushEnabled
			FROM users u
			LEFT JOIN user_settings s ON s.user_id = u.id
			WHERE u.activated = TRUE
			  AND u.streak_days >= :minStreak
			  AND NOT EXISTS (SELECT 1 FROM notifications n
			                  WHERE n.dedupe_key = CONCAT('STREAK_WARNING:', u.id, ':', :todayVn))
			""", nativeQuery = true)
	List<StreakWarningCandidate> findStreakWarningCandidates(@Param("minStreak") int minStreak,
			@Param("todayVn") LocalDate todayVn);

	interface StreakWarningCandidate {
		Long getUserId();

		// ponytail: UI language moved to the device (migration_remove_device_preferences.sql), so this is always
		// null and pushes render Vietnamese; send the locale on device register when English pushes matter.
		String getUiLanguage();

		Boolean getPushEnabled();
	}

	interface SrsReminderCandidate {
		Long getUserId();

		String getUiLanguage();

		boolean isPushEnabled();

		String getFirstName();
	}

	/** J3: run after {@link UserNotificationRepository#deleteOfNotificationsCreatedBefore} has emptied them (FK). */
	@Modifying
	@Query(value = "DELETE FROM notifications WHERE type IN (:types) AND created_at < :cutoff LIMIT :batch",
			nativeQuery = true)
	int deleteCreatedBefore(@Param("types") Collection<String> types, @Param("cutoff") Instant cutoff,
			@Param("batch") int batch);
}
