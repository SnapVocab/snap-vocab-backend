package vn.ptit.snapvocab.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.notification.repository.DeviceTokenRepository;
import vn.ptit.snapvocab.notification.repository.NotificationRepository;
import vn.ptit.snapvocab.notification.repository.UserNotificationRepository;
import vn.ptit.snapvocab.progress.repository.StreakFreezeDayRepository;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.repository.FsrsRecordRepository;

/**
 * J1–J3 (notification.md §4.4, AC-01…06, AC-20, AC-21) on LOCAL MySQL against a fake Expo Push API. Jobs are built
 * here around the fake URL with no backoff, so the Spring context stays the one the HTTP tests share.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class NotificationJobsTest {

	/** 20:00 in Vietnam on 2026-10-18. */
	private static final Instant AT_20 = Instant.parse("2026-10-18T13:00:00Z");
	/** 21:00 in Vietnam on 2026-10-18. */
	private static final Instant AT_21 = Instant.parse("2026-10-18T14:00:00Z");

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;

	@Autowired NotificationRepository notifications;
	@Autowired UserNotificationRepository inbox;
	@Autowired DeviceTokenRepository devices;
	@Autowired FsrsRecordRepository fsrs;
	@Autowired UserRepository users;
	@Autowired UserItemRepository userItems;
	@Autowired StreakFreezeDayRepository frozenDays;
	@Autowired PlatformTransactionManager txManager;
	@Autowired JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private final List<Long> userIds = new ArrayList<>();
	/** Every batch Expo received. */
	private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
	/** Answer per call number: an HTTP status (200 = one ticket per message from {@link #ticket}). */
	private IntFunction<Integer> status = call -> 200;
	private IntFunction<String> ticket = i -> "{\"status\":\"ok\",\"id\":\"t" + i + "\"}";
	private HttpServer expo;
	private NotificationJobs jobs;
	/** Learners {@link #preview} pretends hold one Streak Freeze unit, so no shop item has to exist. */
	private final List<Long> holdingFreeze = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		expo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		expo.createContext("/push", exchange -> {
			JsonNode batch = json.readTree(exchange.getRequestBody());
			requests.add(batch);
			int code = status.apply(requests.size());
			StringBuilder body = new StringBuilder("{\"data\":[");
			for (int i = 0; i < batch.size(); i++) {
				body.append(i == 0 ? "" : ",").append(ticket.apply(i));
			}
			byte[] bytes = (code == 200 ? body.append("]}").toString() : "{}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(code, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		expo.start();
		PushSender sender = new PushSender("http://127.0.0.1:" + expo.getAddress().getPort() + "/push", "",
				new long[] {0, 0, 0}, devices, txManager);
		StreakFreezeService freeze = Mockito.spy(new StreakFreezeService(userItems, frozenDays));
		Mockito.doAnswer(this::preview).when(freeze).preview(Mockito.any(), Mockito.any());
		jobs = new NotificationJobs(notifications, inbox, devices, fsrs, users, freeze, sender, txManager, true, true);
	}

	@AfterEach
	void cleanUp() {
		expo.stop(0);
		for (Long id : userIds) {
			jdbc.update("DELETE FROM fsrs_records WHERE user_id = ?", id);
			jdbc.update("DELETE FROM device_tokens WHERE user_id = ?", id);
			List<Long> mine = jdbc.queryForList("SELECT notification_id FROM user_notifications WHERE user_id = ?", Long.class, id);
			jdbc.update("DELETE FROM user_notifications WHERE user_id = ?", id);
			mine.forEach(n -> jdbc.update("DELETE FROM notifications WHERE id = ?", n));
			jdbc.update("DELETE FROM user_settings WHERE user_id = ?", id);
			jdbc.update("DELETE FROM users WHERE id = ?", id);
		}
	}

	@Test
	void remindsOnceAtTheChosenTimeAndPushesEveryDevice() { // AC-01, AC-04
		long lan = learner("Lan", "vi", LocalTime.of(20, 0), true);
		String token = token(lan);
		dueCards(lan, 12, 2);
		dueCards(lan, 5, 4); // SUSPENDED: never due

		jobs.runSrsReminders(AT_20);

		Map<String, Object> n = jdbc.queryForMap("SELECT n.title, n.content, n.route, n.dedupe_key, n.type, un.is_read, un.id AS un_id"
				+ " FROM notifications n JOIN user_notifications un ON un.notification_id = n.id WHERE un.user_id = ?", lan);
		assertThat(n).containsEntry("title", "Đến giờ ôn tập rồi!")
				.containsEntry("content", "Lan ơi, bạn có 12 từ cần ôn. Ôn ngay để nhớ lâu hơn nhé!")
				.containsEntry("route", "/study/select").containsEntry("type", "SRS_REMINDER")
				.containsEntry("dedupe_key", "SRS_REMINDER:" + lan + ":2026-10-18");
		JsonNode push = pushTo(token);
		assertThat(push.path("data").path("route").asText()).isEqualTo("/study/select");
		assertThat(push.path("data").path("userNotificationId").asLong()).isEqualTo(((Number) n.get("un_id")).longValue());
		assertThat(push.path("data").path("userId").asLong()).isEqualTo(lan);
		assertThat(push.path("ttl").asInt()).isEqualTo(14400);
		assertThat(push.path("channelId").asText()).isEqualTo("srs-reminder");

		int pushes = pushesTo(token);
		jobs.runSrsReminders(AT_20.plusSeconds(300));
		jobs.runSrsReminders(AT_20.plusSeconds(1500));
		assertThat(inboxRows(lan)).isOne();
		assertThat(pushesTo(token)).isEqualTo(pushes);
	}

	@Test
	void noDueCardsNoReminderPushOffInAppOnlyNoTimeNothing() { // AC-02, AC-03, AC-05
		long empty = learner("Empty", "vi", LocalTime.of(20, 0), true);
		token(empty);
		long quiet = learner("Quiet", "en", LocalTime.of(19, 45), false);
		String quietToken = token(quiet);
		dueCards(quiet, 3, 2);
		long unset = learner("Unset", "vi", null, true);
		dueCards(unset, 50, 2);
		long later = learner("Later", "vi", LocalTime.of(20, 5), true);
		dueCards(later, 2, 2);

		jobs.runSrsReminders(AT_20);

		assertThat(inboxRows(empty)).isZero();
		assertThat(inboxRows(unset)).isZero();
		assertThat(inboxRows(later)).isZero();
		assertThat(inboxRows(quiet)).isOne();
		assertThat(jdbc.queryForObject("SELECT n.content FROM notifications n JOIN user_notifications un"
				+ " ON un.notification_id = n.id WHERE un.user_id = ?", String.class, quiet))
				.isEqualTo("Quiet, you have 3 words to review. Review now to remember them longer!");
		assertThat(pushesTo(quietToken)).isZero();
	}

	@Test
	void deadTokensAreRemovedAndServerErrorsAreRetried() { // AC-20, J2 retry
		long lan = learner("Lan", "vi", LocalTime.of(20, 0), true);
		token(lan);
		token(lan);
		dueCards(lan, 1, 2);
		status = call -> call == 1 ? 503 : 200;
		// Second message of the batch: Expo says the device is gone.
		ticket = i -> i == 0 ? "{\"status\":\"ok\"}"
				: "{\"status\":\"error\",\"message\":\"gone\",\"details\":{\"error\":\"DeviceNotRegistered\"}}";
		List<String> sendOrder = devices.findTokensByUserId(lan);

		jobs.runSrsReminders(AT_20);

		assertThat(requests).hasSize(2); // 503, then the retry
		assertThat(devices.findTokensByUserId(lan)).containsExactly(sendOrder.get(0));
	}

	@Test
	void retentionDropsOldNotificationsAndIdleDevices() { // AC-21, J3
		long lan = learner("Lan", "vi", LocalTime.of(20, 0), true);
		String idle = token(lan);
		String fresh = token(lan);
		dueCards(lan, 1, 2);
		jobs.runSrsReminders(AT_20);
		jdbc.update("UPDATE notifications n JOIN user_notifications un ON un.notification_id = n.id"
				+ " SET n.created_at = NOW(6) - INTERVAL 91 DAY WHERE un.user_id = ?", lan);
		jdbc.update("UPDATE device_tokens SET last_seen_at = NOW(6) - INTERVAL 61 DAY WHERE token = ?", idle);

		jobs.purge(Instant.now());

		assertThat(inboxRows(lan)).isZero();
		assertThat(devices.findTokensByUserId(lan)).containsExactly(fresh);
	}

	@Test
	void aNewReminderSupersedesTheUnreadOneAndPushesTheUnreadCount() { // UI-NOTIF-05, OQ-NOTIF-06
		long lan = learner("Lan", "vi", LocalTime.of(20, 0), true);
		String token = token(lan);
		dueCards(lan, 3, 2);
		jobs.runSrsReminders(AT_20.minus(1, ChronoUnit.DAYS));

		jobs.runSrsReminders(AT_20);

		assertThat(jdbc.queryForList("SELECT un.is_read FROM user_notifications un WHERE un.user_id = ? ORDER BY un.id",
				Boolean.class, lan)).containsExactly(true, false);
		assertThat(messagesTo(token)).extracting(m -> m.path("badge").asInt()).containsExactly(1, 1);
	}

	@Test
	void remindersLiveSevenDaysOtherNotificationsNinety() { // UI-NOTIF-05, OQ-NOTIF-05
		long lan = learner("Lan", "vi", LocalTime.of(20, 0), true);
		dueCards(lan, 1, 2);
		jobs.runSrsReminders(AT_20);
		jdbc.update("INSERT INTO notifications (type, title, content, created_at) VALUES ('SYSTEM', 't', 'c', NOW(6))");
		long system = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO user_notifications (user_id, notification_id, is_read) VALUES (?, ?, FALSE)", lan, system);
		jdbc.update("UPDATE notifications n JOIN user_notifications un ON un.notification_id = n.id"
				+ " SET n.created_at = NOW(6) - INTERVAL 8 DAY WHERE un.user_id = ?", lan);

		jobs.purge(Instant.now());

		assertThat(jdbc.queryForList("SELECT notification_id FROM user_notifications WHERE user_id = ?", Long.class, lan))
				.containsExactly(system);
	}

	@Test
	void warnsAt21OnlyStreaksOfThreeNotYetStudiedToday() { // OQ-NOTIF-06 = B
		long lan = streaker("Lan", 5, "2026-10-17 05:00:00"); // studied yesterday
		String token = token(lan);
		long done = streaker("Done", 5, "2026-10-18 05:00:00"); // studied today
		long young = streaker("Young", 2, "2026-10-17 05:00:00");
		long broken = streaker("Broken", 5, "2026-10-15 05:00:00"); // chain already gone, not yet settled
		long kept = streaker("Kept", 4, "2026-10-17 05:00:00");
		holdingFreeze.add(kept);

		jobs.runStreakWarnings(AT_21);
		jobs.runStreakWarnings(AT_21.plusSeconds(300));

		Map<String, Object> n = jdbc.queryForMap("SELECT n.title, n.content, n.type, n.route, n.dedupe_key"
				+ " FROM notifications n JOIN user_notifications un ON un.notification_id = n.id WHERE un.user_id = ?", lan);
		assertThat(n).containsEntry("title", "Giữ chuỗi 5 ngày nhé!")
				.containsEntry("content", "Bạn chưa học hôm nay. Học trước 0 giờ để giữ chuỗi 5 ngày nhé!")
				.containsEntry("type", "STREAK_WARNING").containsEntry("route", "/study/select")
				.containsEntry("dedupe_key", "STREAK_WARNING:" + lan + ":2026-10-18");
		assertThat(pushTo(token).path("data").path("type").asText()).isEqualTo("STREAK_WARNING");
		assertThat(inboxRows(done)).isZero();
		assertThat(inboxRows(young)).isZero();
		assertThat(inboxRows(broken)).isZero();
		assertThat(jdbc.queryForObject("SELECT n.content FROM notifications n JOIN user_notifications un"
				+ " ON un.notification_id = n.id WHERE un.user_id = ?", String.class, kept))
				.isEqualTo("Bạn chưa học hôm nay. Nếu không học trước 0 giờ, 1 Đóng băng chuỗi sẽ được dùng để giữ chuỗi.");
	}

	// ---- helpers ----

	/** Real settlement, with one unit added for learners in {@link #holdingFreeze}. */
	private Object preview(InvocationOnMock call) throws Throwable {
		StreakFreezeService.Settlement s = (StreakFreezeService.Settlement) call.callRealMethod();
		User user = call.getArgument(0);
		return holdingFreeze.contains(user.getId())
				? new StreakFreezeService.Settlement(s.consumed(), s.broken(), s.coveredThrough(), 1, s.lastFrozen())
				: s;
	}

	/** {@code lastStudiedUtc}: users.last_studied_at, stored in UTC. */
	private long streaker(String firstName, int streak, String lastStudiedUtc) {
		long id = learner(firstName, "vi", null, true);
		jdbc.update("UPDATE users SET streak_days = ?, last_studied_at = ? WHERE id = ?", streak, lastStudiedUtc, id);
		return id;
	}

	private long learner(String firstName, String language, LocalTime reminderAt, boolean pushEnabled) {
		jdbc.update("INSERT INTO users (activated, first_name, password_hash, email, longest_streak_days, created_at, "
				+ "updated_at) VALUES (TRUE, ?, REPEAT('x', 60), CONCAT('notif-job-it-', UUID(), '@test.local'), 0, NOW(6), NOW(6))",
				firstName);
		long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		userIds.add(id);
		jdbc.update("INSERT INTO user_settings (user_id, ui_language, theme, daily_goal, study_session_card_limit, "
				+ "srs_reminder_at, push_enabled, created_at, updated_at) VALUES (?, ?, 'SYSTEM', 20, 20, ?, ?, NOW(6), NOW(6))",
				id, language, reminderAt, pushEnabled);
		return id;
	}

	private String token(long userId) {
		String token = "ExponentPushToken[it-" + UUID.randomUUID() + "]";
		jdbc.update("INSERT INTO device_tokens (user_id, token, platform, created_at, last_seen_at)"
				+ " VALUES (?, ?, 'ANDROID', NOW(6), NOW(6))", userId, token);
		return token;
	}

	/**
	 * {@code count} cards due a fortnight before AT_20, in {@code state} (CardState ordinal: 2 REVIEW, 4 SUSPENDED).
	 * FK checks are off on this one connection only: J1 counts fsrs_records and needs no topic tree behind them.
	 */
	private void dueCards(long userId, int count, int state) {
		jdbc.execute((java.sql.Connection c) -> {
			try (Statement s = c.createStatement()) {
				s.execute("SET FOREIGN_KEY_CHECKS = 0");
				for (int i = 0; i < count; i++) {
					s.execute("INSERT INTO fsrs_records (user_id, topic_item_id, state, due, stability, difficulty, reps, lapses,"
							+ " created_at, updated_at) VALUES (" + userId + ", " + (900_000_000L + state * 1000 + i) + ", " + state
							+ ", '2026-10-04 00:00:00', 1, 5, 1, 0, NOW(6), NOW(6))");
				}
				s.execute("SET FOREIGN_KEY_CHECKS = 1");
			}
			return null;
		});
	}

	private long inboxRows(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ?", Long.class, userId);
	}

	private List<JsonNode> messagesTo(String token) {
		List<JsonNode> out = new ArrayList<>();
		synchronized (requests) {
			requests.forEach(batch -> batch.forEach(m -> {
				if (m.path("to").asText().equals(token)) {
					out.add(m);
				}
			}));
		}
		return out;
	}

	private JsonNode pushTo(String token) {
		List<JsonNode> m = messagesTo(token);
		assertThat(m).hasSize(1);
		return m.get(0);
	}

	private int pushesTo(String token) {
		return messagesTo(token).size();
	}
}
