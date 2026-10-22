package vn.ptit.snapvocab.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.notification.domain.UserNotification;

/** Every learner-facing query filters by the token's user id, never by id alone (notification.md §4.2). */
@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

	/** A1, newest first. First page: pass {@code Long.MAX_VALUE} as the cursor. */
	@EntityGraph(attributePaths = "notification")
	List<UserNotification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Limit limit);

	/** A2. */
	long countByUserIdAndIsReadFalse(Long userId);

	/** A3: empty when the id is unknown or someone else's, both answered 404. */
	Optional<UserNotification> findByIdAndUserId(Long id, Long userId);

	/** A4. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE UserNotification un SET un.isRead = true, un.readAt = :now "
			+ "WHERE un.user.id = :userId AND un.isRead = false")
	int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

	/**
	 * A8: one inbox row for every user activated right now; returns the recipient count.
	 * ponytail: single INSERT…SELECT, fine up to ~100k users; batch by users.id beyond that.
	 */
	@Modifying
	@Query(value = "INSERT INTO user_notifications (user_id, notification_id, is_read) "
			+ "SELECT id, :notificationId, FALSE FROM users WHERE activated = TRUE", nativeQuery = true)
	int fanOut(@Param("notificationId") Long notificationId);

	/** J3: call until it returns less than {@code batch}. */
	@Modifying
	@Query(value = "DELETE FROM user_notifications WHERE notification_id IN "
			+ "(SELECT id FROM notifications WHERE created_at < :cutoff) LIMIT :batch", nativeQuery = true)
	int deleteOfNotificationsCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
