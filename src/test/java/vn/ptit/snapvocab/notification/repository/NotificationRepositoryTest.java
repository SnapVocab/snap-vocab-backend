package vn.ptit.snapvocab.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.notification.domain.DeviceToken;
import vn.ptit.snapvocab.notification.domain.Notification;
import vn.ptit.snapvocab.notification.domain.UserNotification;
import vn.ptit.snapvocab.notification.domain.enumeration.DevicePlatform;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;
import vn.ptit.snapvocab.notification.repository.NotificationRepository.SrsReminderCandidate;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** Native SQL and DB constraints behind notification.md §4–5, on LOCAL MySQL. */
@LocalMySqlJpaTest
class NotificationRepositoryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

	@Autowired
	NotificationRepository notifications;
	@Autowired
	UserNotificationRepository inbox;
	@Autowired
	DeviceTokenRepository devices;
	@Autowired
	UserRepository users;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager txManager;

	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> notificationIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (Long id : notificationIds) {
			jdbc.update("DELETE FROM user_notifications WHERE notification_id = ?", id);
			jdbc.update("DELETE FROM notifications WHERE id = ?", id);
		}
		for (Long id : userIds) {
			jdbc.update("DELETE FROM user_notifications WHERE user_id = ?", id);
			jdbc.update("DELETE FROM user_settings WHERE user_id = ?", id);
			jdbc.update("DELETE FROM users WHERE id = ?", id);
		}
	}

	@Test
	void srsReminderCandidatesHonourWindowActivationAndTodaysDedupeKey() {
		long lan = user(true, "Lan", LocalTime.of(20, 0), false);
		long inactive = user(false, "Off", LocalTime.of(20, 0), true);
		long early = user(true, "Early", LocalTime.of(19, 0), true);
		long noReminder = user(true, "None", null, true);
		long midnight = user(true, "Mid", LocalTime.MIDNIGHT, true);

		List<SrsReminderCandidate> window = candidates(LocalTime.of(19, 30), LocalTime.of(20, 0), TODAY);
		assertThat(window).extracting(SrsReminderCandidate::getUserId).containsExactly(lan)
				.doesNotContain(inactive, early, noReminder);
		assertThat(window.get(0).getFirstName()).isEqualTo("Lan");
		assertThat(window.get(0).getUiLanguage()).isEqualTo("vi");
		assertThat(window.get(0).isPushEnabled()).isFalse();
		assertThat(candidates(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, TODAY))
				.extracting(SrsReminderCandidate::getUserId).containsExactly(midnight);

		notification(NotificationType.SRS_REMINDER, Notification.srsReminderDedupeKey(lan, TODAY.minusDays(1)));
		assertThat(candidates(LocalTime.of(19, 30), LocalTime.of(20, 0), TODAY)).hasSize(1);
		notification(NotificationType.SRS_REMINDER, Notification.srsReminderDedupeKey(lan, TODAY));
		assertThat(candidates(LocalTime.of(19, 30), LocalTime.of(20, 0), TODAY)).isEmpty();
	}

	@Test
	void dedupeKeyIsUniqueAndNullsAreNot() {
		notification(NotificationType.SYSTEM, null);
		notification(NotificationType.SYSTEM, null);
		notification(NotificationType.SYSTEM, Notification.systemDedupeKey("maintenance-2026-10-20"));

		assertThatThrownBy(() -> notification(NotificationType.SYSTEM, Notification.systemDedupeKey("maintenance-2026-10-20")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void inboxPagesByCursorCountsUnreadAndMarksAllRead() {
		long lan = user(true, "Lan", null, true);
		long minh = user(true, "Minh", null, true);
		long system = notification(NotificationType.SYSTEM, null);
		long activated = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE activated = TRUE", Long.class);
		assertThat(tx(() -> inbox.fanOut(system))).isEqualTo((int) activated);
		long second = deliver(lan, notification(NotificationType.SRS_REMINDER, null));
		long third = deliver(lan, notification(NotificationType.SRS_REMINDER, null));

		List<UserNotification> first = inbox.findByUserIdAndIdLessThanOrderByIdDesc(lan, Long.MAX_VALUE, Limit.of(2));
		assertThat(first).extracting(UserNotification::getId).containsExactly(third, second);
		assertThat(first.get(0).getNotification().getTitle()).isEqualTo("t");
		List<UserNotification> rest = inbox.findByUserIdAndIdLessThanOrderByIdDesc(lan, second, Limit.of(2));
		assertThat(rest).hasSize(1);
		assertThat(rest.get(0).getNotification().getId()).isEqualTo(system);

		assertThat(inbox.findByIdAndUserId(third, lan)).isPresent();
		assertThat(inbox.findByIdAndUserId(third, minh)).isEmpty();

		assertThat(inbox.countByUserIdAndIsReadFalse(lan)).isEqualTo(3);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		assertThat(tx(() -> inbox.markAllRead(lan, now))).isEqualTo(3);
		assertThat(tx(() -> inbox.markAllRead(lan, now.plusSeconds(60)))).isZero();
		assertThat(inbox.countByUserIdAndIsReadFalse(lan)).isZero();
		assertThat(inbox.findById(third).orElseThrow().getReadAt()).isEqualTo(now);
		assertThat(inbox.countByUserIdAndIsReadFalse(minh)).isEqualTo(1);
	}

	@Test
	void deviceTokenUpsertMovesTokenAndUserDeleteCascades() {
		long lan = user(true, "Lan", null, true);
		long minh = user(true, "Minh", null, true);
		String token = "ExponentPushToken[it-" + System.nanoTime() + "]";
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		tx(() -> devices.upsert(lan, token, "ANDROID", now));
		tx(() -> devices.upsert(lan, token, "ANDROID", now.plusSeconds(1)));
		assertThat(devices.findOwnerId(token)).contains(lan);
		tx(() -> devices.upsert(minh, token, "IOS", now.plusSeconds(2)));

		assertThat(devices.findOwnerId(token)).contains(minh);
		assertThat(devices.findTokensByUserId(lan)).isEmpty();
		assertThat(devices.findTokensByUserId(minh)).containsExactly(token);
		DeviceToken row = devices.findAll().stream().filter(d -> d.getToken().equals(token)).findFirst().orElseThrow();
		assertThat(row.getPlatform()).isEqualTo(DevicePlatform.IOS);
		assertThat(row.getCreatedAt()).isEqualTo(now);
		assertThat(row.getLastSeenAt()).isEqualTo(now.plusSeconds(2));

		assertThat(tx(() -> devices.deleteOwned(token, lan))).isZero();
		assertThat(tx(() -> devices.deleteNotSeenSince(now.plusSeconds(2)))).isZero();
		assertThat(tx(() -> devices.deleteNotSeenSince(now.plusSeconds(3)))).isEqualTo(1);

		tx(() -> devices.upsert(minh, token, "IOS", now));
		assertThat(tx(() -> devices.deleteByTokens(List.of(token)))).isEqualTo(1);
		tx(() -> devices.upsert(minh, token, "IOS", now));
		assertThat(tx(() -> devices.deleteOwned(token, minh))).isEqualTo(1);

		tx(() -> devices.upsert(minh, token, "IOS", now));
		jdbc.update("DELETE FROM user_settings WHERE user_id = ?", minh);
		jdbc.update("DELETE FROM users WHERE id = ?", minh);
		assertThat(devices.findOwnerId(token)).isEmpty();
	}

	@Test
	void retentionDeletesInboxRowsInBatchesThenTheNotifications() {
		long lan = user(true, "Lan", null, true);
		long old = notification(NotificationType.SYSTEM, null);
		long oldToo = notification(NotificationType.SYSTEM, null);
		long fresh = notification(NotificationType.SYSTEM, null);
		deliver(lan, old);
		deliver(lan, oldToo);
		deliver(lan, fresh);
		jdbc.update("UPDATE notifications SET created_at = NOW(6) - INTERVAL 91 DAY WHERE id IN (?, ?)", old, oldToo);
		Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);

		assertThat(tx(() -> inbox.deleteOfNotificationsCreatedBefore(cutoff, 1))).isEqualTo(1);
		assertThat(tx(() -> inbox.deleteOfNotificationsCreatedBefore(cutoff, 1))).isEqualTo(1);
		assertThat(tx(() -> inbox.deleteOfNotificationsCreatedBefore(cutoff, 1))).isZero();
		assertThat(tx(() -> notifications.deleteCreatedBefore(cutoff, 5000))).isEqualTo(2);

		assertThat(notifications.findAllById(List.of(old, oldToo, fresh))).extracting(Notification::getId)
				.containsExactly(fresh);
		assertThat(inbox.countByUserIdAndIsReadFalse(lan)).isEqualTo(1);
	}

	// --- helpers ---------------------------------------------------------------------------------

	private List<SrsReminderCandidate> candidates(LocalTime start, LocalTime end, LocalDate day) {
		return notifications.findSrsReminderCandidates(start, end, day).stream()
				.filter(c -> userIds.contains(c.getUserId())).toList();
	}

	private long user(boolean activated, String firstName, LocalTime reminderAt, boolean pushEnabled) {
		jdbc.update("INSERT INTO users (activated, first_name, password_hash, email, longest_streak_days, created_at, "
				+ "updated_at) VALUES (?, ?, REPEAT('x', 60), CONCAT('notif-it-', UUID(), '@test.local'), 0, NOW(6), NOW(6))",
				activated, firstName);
		long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		userIds.add(id);
		jdbc.update("INSERT INTO user_settings (user_id, ui_language, theme, daily_goal, study_session_card_limit, "
				+ "srs_reminder_at, push_enabled, created_at, updated_at) VALUES (?, 'vi', 'SYSTEM', 20, 20, ?, ?, NOW(6), NOW(6))",
				id, reminderAt, pushEnabled);
		return id;
	}

	private long notification(NotificationType type, String dedupeKey) {
		Notification saved = notifications.saveAndFlush(Notification.builder().title("t").content("c").type(type)
				.dedupeKey(dedupeKey).build());
		notificationIds.add(saved.getId());
		return saved.getId();
	}

	private long deliver(long userId, long notificationId) {
		return tx(() -> inbox.save(UserNotification.builder().user(users.getReferenceById(userId))
				.notification(notifications.getReferenceById(notificationId)).build()).getId());
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(txManager).execute(status -> work.get());
	}
}
