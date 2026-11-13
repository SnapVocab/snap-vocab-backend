package vn.ptit.snapvocab.adminstats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.ItemSales;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.MissionStat;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.UnmappedLabel;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ErrorCount;
import vn.ptit.snapvocab.adminstats.service.AdminOverviewService;
import vn.ptit.snapvocab.adminstats.service.StatsWindow;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.service.ScanDetectionBackfill;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/**
 * admin_overview_backend.md §6 on LOCAL MySQL. Scan tables are wiped per test (nothing else reads them); every other
 * number is asserted as a before/after delta, so data other tests leave behind does not matter. Same mocks as
 * AdminAuditHttpTest, so the Spring context is shared.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminOverviewHttpTest {

	private static final String PREFIX = "IT_OV_";
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
			.withZone(ZoneOffset.UTC);

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;

	@LocalServerPort
	int port;
	@Autowired
	JwtEncoder jwtEncoder;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	AdminOverviewService service;
	@Autowired
	ScanDetectionBackfill backfill;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private final StatsWindow w = StatsWindow.now(ProgressService.ZONE);
	private final Instant todayStart = w.start(w.today());
	private User a;
	private User b;
	private User admin;

	@BeforeEach
	void setUp() {
		cleanUp();
		a = saveUser(true);
		b = saveUser(true);
		admin = grantAdmin(saveUser(true));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM scan_detections");
		jdbc.update("DELETE FROM scan_requests");
		String ours = "SELECT id FROM users WHERE email LIKE 'overview-it-%'";
		for (String sql : List.of(
				"DELETE FROM quizzes WHERE user_id IN (" + ours + ")",
				"DELETE FROM topic_items WHERE topic_id IN (SELECT t.id FROM topics t JOIN collections c"
						+ " ON c.id = t.collection_id WHERE c.owner_id IN (" + ours + "))",
				"DELETE FROM topics WHERE collection_id IN (SELECT id FROM collections WHERE owner_id IN (" + ours + "))",
				"DELETE FROM collections WHERE owner_id IN (" + ours + ")",
				"DELETE FROM learning_events WHERE user_id IN (" + ours + ")",
				"DELETE FROM coin_transactions WHERE user_id IN (" + ours + ")",
				"DELETE FROM user_daily_missions WHERE user_id IN (" + ours + ")",
				"DELETE FROM user_authorities WHERE user_id IN (" + ours + ")",
				"DELETE FROM users WHERE email LIKE 'overview-it-%'",
				"DELETE FROM mission_templates WHERE code LIKE '" + PREFIX + "%'",
				"DELETE FROM shop_items WHERE code LIKE '" + PREFIX + "%'",
				"DELETE FROM words WHERE word LIKE 'it-ov-%'")) {
			jdbc.update(sql);
		}
	}

	// D-03 runs through every test: the admin gets the same activity as the learners and must change nothing.

	@Test
	void learnersCountActivityNotAdminsOrUnverifiedAccounts() { // D-04, D-05, AC-DASH-01/02
		AdminOverviewDTO before = service.overview();
		saveUser(true);
		saveUser(false);
		grantAdmin(saveUser(true));
		long topic = topic(a);
		long adminTopic = topic(admin);
		for (User u : List.of(a, admin)) {
			for (int i = 0; i < 3; i++) {
				event(u, "SRS_REVIEW_COMPLETED", todayStart.plusSeconds(60 + i));
			}
			event(u, "QUIZ_COMPLETED", todayStart.plusSeconds(120));
		}
		event(b, "QUIZ_COMPLETED", todayStart.minus(1, ChronoUnit.HOURS));
		quiz(a, topic);
		quiz(admin, adminTopic);
		topicItem(topic, todayStart.plusSeconds(5));
		topicItem(topic, todayStart.plusSeconds(6));
		topicItem(topic, todayStart.minusSeconds(1)); // yesterday
		topicItem(adminTopic, todayStart.plusSeconds(5));
		missions(b, 5, admin);
		missions(a, 3);

		AdminOverviewDTO after = service.overview();
		assertThat(after.learners().activated() - before.learners().activated()).isEqualTo(1);
		assertThat(after.learners().activeToday() - before.learners().activeToday()).isEqualTo(1);
		assertThat(after.learners().active7d() - before.learners().active7d()).isEqualTo(2);
		assertThat(after.learners().daily().get(12).active() - before.learners().daily().get(12).active()).isEqualTo(1);
		assertThat(after.learners().daily().get(13).active()).isEqualTo(after.learners().activeToday());
		AdminOverviewDTO.Today t0 = before.learners().today();
		AdminOverviewDTO.Today t1 = after.learners().today();
		assertThat(t1.reviews() - t0.reviews()).isEqualTo(3);
		assertThat(t1.quizzes() - t0.quizzes()).isEqualTo(1);
		assertThat(t1.newWords() - t0.newWords()).isEqualTo(2);
		assertThat(t1.missionsCompleted() - t0.missionsCompleted()).isEqualTo(8);
		assertThat(t1.fullSets() - t0.fullSets()).isEqualTo(1);
		assertThat(after.missions()).filteredOn(m -> m.code().startsWith(PREFIX))
				.extracting(MissionStat::code, MissionStat::assigned7d, MissionStat::completed7d)
				.containsExactlyInAnyOrder(tuple("IT_OV_1", 2L, 2L), tuple("IT_OV_2", 2L, 2L),
						tuple("IT_OV_3", 2L, 2L), tuple("IT_OV_4", 2L, 1L), tuple("IT_OV_5", 2L, 1L));
	}

	@Test
	void scanCountsSplitRefusalsFromErrors() { // D-06, AC-DASH-03
		for (User u : List.of(a, admin)) {
			scan(u, "DONE", null, 1000);
			scan(u, "DONE", null, 3000);
			scan(u, "FAILED", "AI_TIMEOUT", 0);
			scan(u, "FAILED", "AI_QUEUE_FULL", 0);
			scan(u, "FAILED", "QUOTA_EXCEEDED", 0);
			scan(u, "FAILED", "QUOTA_EXCEEDED", 0);
		}
		scan(b, "PENDING", null, 0);

		AdminOverviewDTO.Scan s = service.overview().scan();
		assertThat(s.today()).isEqualTo(new AdminOverviewDTO.ScanToday(4, 2, 1000L, 3000L, 1, 1));
		assertThat(s.daily().get(13).done()).isEqualTo(2);
		assertThat(s.errors7d()).extracting(ErrorCount::code)
				.containsExactlyInAnyOrder(ScanErrorCode.AI_TIMEOUT, ScanErrorCode.AI_QUEUE_FULL);
		assertThat(s.config().minReliability()).isIn("LOW", "MEDIUM", "HIGH");
	}

	@Test
	void latencyIsNearestRankAndNullWithoutSamples() { // D-07, AC-DASH-04
		assertThat(service.overview().scan().today().p50Ms()).isNull();
		assertThat(service.overview().scan().today().p95Ms()).isNull();
		for (int sec = 1; sec <= 100; sec++) {
			scan(a, "DONE", null, sec * 1000L);
		}
		AdminOverviewDTO.ScanToday today = service.overview().scan().today();
		assertThat(today.p50Ms()).isEqualTo(50_000L);
		assertThat(today.p95Ms()).isEqualTo(95_000L);
	}

	@Test
	void unmappedLabelsFollowTheCurrentDictionary() { // D-08, AC-DASH-05
		String label = "it-ov-thermos";
		for (int i = 0; i < 3; i++) {
			detection(scan(a, "DONE", null, 1000), a, label, false);
		}
		detection(scan(admin, "DONE", null, 1000), admin, label, false);
		detection(scan(b, "DONE", null, 1000), b, "it-ov-cup", true);

		assertThat(service.overview().unmappedLabels())
				.containsExactly(new UnmappedLabel(label, 3, 1));

		jdbc.update("INSERT INTO words (word, lang_code, created_at) VALUES (?, 'en', NOW(6))", label);
		assertThat(service.overview().unmappedLabels()).isEmpty();
	}

	@Test
	void coinAndSalesUseVietnamDays() { // D-02, D-09
		AdminOverviewDTO before = service.overview();
		long boost = shopItem("IT_OV_BOOST");
		long frame = shopItem("IT_OV_FRAME");
		coin(a, "MISSION_REWARD", 7, null, todayStart.minusSeconds(1)); // 16:59:59Z → yesterday
		coin(a, "MISSION_REWARD", 50, null, todayStart); // 17:00:00Z → today
		coin(a, "SHOP_PURCHASE", -100, boost, todayStart.plusSeconds(10));
		coin(b, "SHOP_PURCHASE", -100, boost, todayStart.plusSeconds(11));
		coin(a, "SHOP_PURCHASE", -300, frame, todayStart.plusSeconds(12));
		coin(admin, "SHOP_PURCHASE", -100, frame, todayStart.plusSeconds(13));
		coin(admin, "SHOP_PURCHASE", -100, frame, todayStart.plusSeconds(14));
		coin(admin, "MISSION_REWARD", 1000, null, todayStart.plusSeconds(15));

		AdminOverviewDTO after = service.overview();
		assertThat(delta(after, before, 12, true)).isEqualTo(7);
		assertThat(delta(after, before, 13, true)).isEqualTo(50);
		assertThat(delta(after, before, 13, false)).isEqualTo(500);
		assertThat(after.shopSales30d()).filteredOn(s -> s.code().startsWith(PREFIX))
				.containsExactly(new ItemSales("IT_OV_BOOST", 2), new ItemSales("IT_OV_FRAME", 1));
	}

	@Test
	void everySeriesHas14DaysEndingToday() { // D-10
		AdminOverviewDTO o = service.overview();
		String today = w.today().toString();
		assertThat(o.learners().daily()).hasSize(14).last().extracting(AdminOverviewDTO.DayActive::date)
				.isEqualTo(today);
		assertThat(o.scan().daily()).hasSize(14).allSatisfy(d -> assertThat(d.done()).isZero());
		assertThat(o.scan().daily().get(13).date()).isEqualTo(today);
		assertThat(o.economy().daily()).hasSize(14);
		assertThat(o.economy().daily().get(0).date()).isEqualTo(w.from(14).toString());
	}

	@Test
	void backfillWritesLabelsOnceAndSkipsScansWithout() throws Exception { // G2-03
		String withLabels = scan(a, "DONE", null, 1000);
		jdbc.update("UPDATE scan_requests SET result_json = ? WHERE id = ?", """
				{"imageWidth":640,"imageHeight":480,"detections":[
				 {"label":"it-ov-mug","headword":"mug","score":0.9,"source":"od","reliability":"HIGH"},
				 {"label":"it-ov-lamp","headword":null,"score":0.9,"source":"od","reliability":"LOW"}]}""",
				withLabels);
		String empty = scan(a, "DONE", null, 1000);
		jdbc.update("UPDATE scan_requests SET result_json = ? WHERE id = ?",
				"{\"imageWidth\":1,\"imageHeight\":1,\"detections\":[]}", empty);

		assertThat(backfill.backfill()).isEqualTo(2);
		backfill.backfill();

		assertThat(jdbc.queryForList("SELECT label FROM scan_detections WHERE scan_request_id = ? ORDER BY label",
				String.class, withLabels)).containsExactly("it-ov-lamp", "it-ov-mug");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scan_detections", Long.class)).isEqualTo(2);
	}

	@Test
	void onlyAdminsMayRead() throws Exception { // D-11
		assertThat(get(null).statusCode()).isEqualTo(401);
		assertThat(get(bearer(a, "ROLE_USER")).statusCode()).isEqualTo(403);
		HttpResponse<String> ok = get(bearer(admin, "ROLE_USER ROLE_ADMIN"));
		assertThat(ok.statusCode()).isEqualTo(200);
		JsonNode data = json.readTree(ok.body()).path("data");
		assertThat(data.path("learners").path("daily")).hasSize(14);
		assertThat(data.path("scan").path("today").path("p50Ms").isNull()).isTrue();
		assertThat(data.path("generatedAt").asText()).isNotBlank();
	}

	// ---- data ----

	private long delta(AdminOverviewDTO after, AdminOverviewDTO before, int day, boolean in) {
		var x = after.economy().daily().get(day);
		var y = before.economy().daily().get(day);
		return in ? x.coinIn() - y.coinIn() : x.coinOut() - y.coinOut();
	}

	private User saveUser(boolean activated) {
		return userRepository.save(User.builder()
				.email("overview-it-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Tester")
				.activated(activated)
				.coin(0L)
				.build());
	}

	private User grantAdmin(User u) {
		jdbc.update("INSERT INTO user_authorities (user_id, authority_name) VALUES (?, 'ROLE_ADMIN')", u.getId());
		return u;
	}

	private void event(User u, String type, Instant at) {
		jdbc.update("INSERT INTO learning_events (user_id, event_type, learning_date, source_key, created_at)"
				+ " VALUES (?, ?, ?, ?, ?)", u.getId(), type, at.atZone(ProgressService.ZONE).toLocalDate(),
				"it-ov-" + UUID.randomUUID(), UTC.format(at));
	}

	private long topic(User owner) {
		jdbc.update("INSERT INTO collections (name, type, owner_id, created_at, updated_at)"
				+ " VALUES ('IT OV', 'USER', ?, NOW(6), NOW(6))", owner.getId());
		Long collection = jdbc.queryForObject("SELECT MAX(id) FROM collections WHERE owner_id = ?", Long.class,
				owner.getId());
		jdbc.update("INSERT INTO topics (name, collection_id, created_at, updated_at) VALUES ('IT OV', ?, NOW(6),"
				+ " NOW(6))", collection);
		return jdbc.queryForObject("SELECT MAX(id) FROM topics WHERE collection_id = ?", Long.class, collection);
	}

	private void topicItem(long topic, Instant at) {
		jdbc.update("INSERT INTO topic_items (topic_id, created_at) VALUES (?, ?)", topic, UTC.format(at));
	}

	private void quiz(User u, long topic) {
		jdbc.update("INSERT INTO quizzes (user_id, topic_id, topic_name, mode, status, requested_count,"
				+ " create_idempotency_key, created_at, updated_at, completed_at) VALUES (?, ?, 'IT OV', 'MCQ',"
				+ " 'COMPLETED', 5, ?, NOW(6), NOW(6), ?)", u.getId(), topic, UUID.randomUUID().toString(),
				UTC.format(todayStart.plusSeconds(30)));
	}

	/** Five missions today (codes IT_OV_1…5), the first {@code completed} of them done; also for {@code others}. */
	private void missions(User u, int completed, User... others) {
		for (int slot = 1; slot <= 5; slot++) {
			String code = PREFIX + slot;
			jdbc.update("INSERT IGNORE INTO mission_templates (code, name, description, category, trigger_event,"
					+ " target_value, reward_coin, reward_xp, created_at, updated_at) VALUES (?, 'IT', 'IT', 'SRS',"
					+ " 'SRS_REVIEW_COMPLETED', 1, 10, 0, NOW(6), NOW(6))", code);
			Long template = jdbc.queryForObject("SELECT id FROM mission_templates WHERE code = ?", Long.class, code);
			for (User x : concat(u, others)) {
				boolean done = slot <= completed || x != u;
				jdbc.update("INSERT INTO user_daily_missions (user_id, mission_template_id, mission_date, slot, code,"
						+ " name, description, category, trigger_event, target_value, reward_coin, reward_xp,"
						+ " current_progress, status, completed_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?,"
						+ " 'IT', 'IT', 'SRS', 'SRS_REVIEW_COMPLETED', 1, 10, 0, ?, ?, ?, NOW(6), NOW(6))",
						x.getId(), template, w.today(), slot, code, done ? 1 : 0, done ? "COMPLETED" : "IN_PROGRESS",
						done ? UTC.format(todayStart.plusSeconds(1)) : null);
			}
		}
	}

	private static List<User> concat(User u, User... others) {
		return java.util.stream.Stream.concat(java.util.stream.Stream.of(u), java.util.Arrays.stream(others))
				.toList();
	}

	/** A scan created today; DONE ones took {@code ms} between started_at and finished_at. */
	private String scan(User u, String status, String errorCode, long ms) {
		String id = UUID.randomUUID().toString();
		Instant started = todayStart.plusSeconds(1);
		jdbc.update("INSERT INTO scan_requests (id, user_id, status, error_code, created_at, updated_at, started_at,"
				+ " finished_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)", id, u.getId(), status, errorCode,
				UTC.format(todayStart), UTC.format(todayStart), UTC.format(started),
				"DONE".equals(status) ? UTC.format(started.plusMillis(ms)) : null);
		return id;
	}

	private void detection(String scanId, User u, String label, boolean hasWord) {
		jdbc.update("INSERT INTO scan_detections (scan_request_id, user_id, created_at, label, reliability, has_word)"
				+ " VALUES (?, ?, ?, ?, 'HIGH', ?)", scanId, u.getId(), UTC.format(todayStart), label, hasWord);
	}

	private long shopItem(String code) {
		jdbc.update("INSERT INTO shop_items (code, name, type, status, purchasable, sort_order, version, created_at,"
				+ " updated_at) VALUES (?, 'IT', 'AVATAR_FRAME', 'PUBLISHED', TRUE, 0, 0, NOW(6), NOW(6))", code);
		return jdbc.queryForObject("SELECT id FROM shop_items WHERE code = ?", Long.class, code);
	}

	private void coin(User u, String source, long amount, Long itemId, Instant at) {
		jdbc.update("INSERT INTO coin_transactions (user_id, source_type, amount, balance_after, reference_id,"
				+ " event_key, created_at) VALUES (?, ?, ?, 0, ?, ?, ?)", u.getId(), source, amount,
				itemId == null ? null : itemId.toString(), "it-ov-" + UUID.randomUUID(), UTC.format(at));
	}

	private HttpResponse<String> get(String token) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/overview"));
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		return http.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private String bearer(User u, String authorities) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
				.subject(u.getEmail())
				.claim(SecurityUtils.AUTHORITIES_CLAIM, authorities)
				.claim(SecurityUtils.USER_ID_CLAIM, u.getId())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims))
				.getTokenValue();
	}
}
