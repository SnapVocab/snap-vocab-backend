package vn.ptit.snapvocab.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;
import vn.ptit.snapvocab.mission.service.MissionCatalogSeeder;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/**
 * admin_audit_log_backend.md §9.2 over HTTP → Security → Shop / Mission services → LOCAL MySQL, then the read API.
 * Same mocks as AdminShopHttpTest, so the Spring context is shared.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminAuditHttpTest {

	private static final String ITEMS = "/api/admin/shop/items";
	private static final String TEMPLATES = "/api/admin/mission-templates";
	private static final String LOG = "/api/admin/audit-log";
	private static final String REASON = "Khung giới hạn mùa xuân, định giá cao hơn.";

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
	MissionTemplateRepository templateRepository;
	@Autowired
	MissionCatalogSeeder seeder;
	@Autowired
	JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private User adminUser;
	private String admin;
	private String user;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		adminUser = saveUser();
		admin = bearer(adminUser, "ROLE_USER ROLE_ADMIN");
		user = bearer(saveUser(), "ROLE_USER");
		StatObjectResponse stat = mock(StatObjectResponse.class);
		when(stat.size()).thenReturn(10_000L);
		when(minioClient.statObject(any())).thenReturn(stat);
		// Topic writes evict "topic-tree"/"topic-items"; the mocked manager would otherwise have no cache to evict.
		when(cacheManager.getCache(anyString())).thenAnswer(inv -> new ConcurrentMapCache(inv.getArgument(0)));
	}

	@AfterEach
	void cleanUp() {
		for (String table : List.of("user_daily_mission_claims", "user_daily_mission_progress_logs",
				"user_daily_missions", "mission_templates", "shop_items", "admin_audit_logs")) {
			jdbc.update("DELETE FROM " + table);
		}
		// Collections a failed run left behind (a passing run deletes them through the API).
		String ours = "SELECT id FROM collections WHERE name LIKE 'IT Audit %' OR name LIKE 'Của tôi %'";
		String topics = "SELECT id FROM topics WHERE collection_id IN (SELECT id FROM (" + ours + ") c)";
		String items = "SELECT id FROM topic_items WHERE topic_id IN (" + topics + ")";
		jdbc.update("DELETE FROM topic_item_attribute_values WHERE group_instance_id IN (SELECT id FROM"
				+ " topic_item_attribute_groups WHERE topic_item_id IN (" + items + "))");
		jdbc.update("DELETE FROM topic_item_attribute_groups WHERE topic_item_id IN (" + items + ")");
		jdbc.update("DELETE FROM topic_items WHERE topic_id IN (" + topics + ")");
		jdbc.update("DELETE FROM topics WHERE collection_id IN (SELECT id FROM (" + ours + ") c)");
		jdbc.update("DELETE FROM collections WHERE id IN (SELECT id FROM (" + ours + ") c)");
		jdbc.update("DELETE FROM users WHERE email LIKE 'audit-it-%'");
	}

	// ---- Shop (S-01…S-09) ----

	@Test
	void shopWritesAreAuditedOnceWithActorVersionAndChanges() throws Exception {
		long id = create(frame("FRAME_A", "Khung A", 900), 201); // S-01
		JsonNode row = rows("targetType=SHOP_ITEM&targetId=" + id).get(0);
		assertThat(row.path("action").asText()).isEqualTo("SHOP_ITEM_CREATED");
		assertThat(row.path("version").asLong()).isZero();
		assertThat(row.path("actor").path("id").asLong()).isEqualTo(adminUser.getId());
		assertThat(row.path("actor").path("email").asText()).isEqualTo(adminUser.getEmail());
		assertThat(row.path("target").path("code").asText()).isEqualTo("FRAME_A");
		assertThat(row.path("target").has("parents")).isFalse();
		assertThat(row.path("changes").findValuesAsText("field")).contains("code", "price").doesNotContain("themeKey");

		long v = version(id);
		patch(id, Map.of("version", v, "price", 950), 200); // S-02
		JsonNode updated = rows("targetType=SHOP_ITEM&targetId=" + id).get(0);
		assertThat(updated.path("action").asText()).isEqualTo("SHOP_ITEM_UPDATED");
		assertThat(updated.path("changes")).hasSize(1);
		assertThat(updated.path("changes").get(0).path("before").asLong()).isEqualTo(900);
		assertThat(updated.path("changes").get(0).path("after").asLong()).isEqualTo(950);
		assertThat(updated.path("version").asLong()).isEqualTo(version(id));
		assertThat(updated.path("guardrail").isNull()).isTrue();
		assertThat(jdbc.queryForObject("SELECT economy_change FROM admin_audit_logs WHERE action = 'SHOP_ITEM_UPDATED'",
				Boolean.class)).isTrue();

		long v2 = version(id);
		patch(id, Map.of("version", v2, "price", 950), 200); // S-03: same value
		assertThat(version(id)).isEqualTo(v2);
		assertThat(error(patch(id, Map.of("version", v, "price", 1000), 409))).isEqualTo("VERSION_CONFLICT"); // S-04
		assertThat(auditCount()).isEqualTo(2);

		long draft = create(frame("FRAME_DRAFT", "Nháp", 300), 201); // S-09
		long draftVersion = version(draft);
		call(admin, "DELETE", ITEMS + "/" + draft, null, 204);
		JsonNode deleted = rows("targetType=SHOP_ITEM&targetId=" + draft).get(0);
		assertThat(deleted.path("action").asText()).isEqualTo("SHOP_ITEM_DELETED");
		assertThat(deleted.path("version").asLong()).isEqualTo(draftVersion);
		assertThat(deleted.path("changes").findValuesAsText("field")).containsExactly("name", "type", "price", "status");
		assertThat(deleted.path("changes").findValues("after")).allMatch(JsonNode::isNull);
	}

	@Test
	void priceOutsideTheBandNeedsAReason() throws Exception {
		publish(create(frame("FRAME_1", "Khung Một", 1000), 201), null, 200);
		publish(create(frame("FRAME_2", "Khung Hai", 1200), 201), null, 200);
		publish(create(frame("FRAME_3", "Huyền Thoại Bất Hủ", 1500), 201), null, 200);
		long id = create(frame("FRAME_CHERRY", "Hoa Anh Đào Mùa Xuân", 900), 201);
		publish(id, null, 200);
		int before = auditCount();

		JsonNode rejected = patch(id, Map.of("version", version(id), "price", 2400), 400); // S-05
		assertThat(error(rejected)).isEqualTo("AUDIT_REASON_REQUIRED");
		String guardrail = "Giá 2.400 Coin cao hơn 1,5 lần giá cao nhất của Khung avatar đang bán (Huyền Thoại Bất Hủ, 1.500 Coin).";
		assertThat(rejected.path("data").path("rule").asText()).isEqualTo("PRICE_OUT_OF_BAND");
		assertThat(rejected.path("data").path("guardrail").asText()).isEqualTo(guardrail);
		assertThat(price(id)).isEqualTo(900);
		assertThat(auditCount()).isEqualTo(before);
		assertThat(error(patch(id, Map.of("version", version(id), "price", 2400, "reason", "ngắn"), 400)))
				.isEqualTo("AUDIT_REASON_INVALID");

		patch(id, Map.of("version", version(id), "price", 2400, "reason", REASON), 200); // S-06
		JsonNode row = rows("kind=GUARDRAIL").get(0);
		assertThat(row.path("reason").asText()).isEqualTo(REASON);
		assertThat(row.path("guardrail").asText()).isEqualTo(guardrail);

		// S-07: a draft past the band (now 2.400 × 1,5 = 3.600) is saved with a reason, but publishing asks again.
		Map<String, Object> pricey = frame("FRAME_PRICEY", "Đắt", 4000);
		assertThat(error(call(admin, "POST", ITEMS, pricey, 400))).isEqualTo("AUDIT_REASON_REQUIRED");
		pricey.put("reason", REASON);
		long draft = create(pricey, 201);
		assertThat(error(publish(draft, null, 400))).isEqualTo("AUDIT_REASON_REQUIRED");
		assertThat(status(draft)).isEqualTo("DRAFT");
		publish(draft, REASON, 200);
		assertThat(status(draft)).isEqualTo("PUBLISHED");
	}

	@Test
	void archiveAlwaysNeedsAReason() throws Exception { // S-08
		long id = create(frame("FRAME_OLD", "Khung Cũ", 500), 201);
		publish(id, null, 200);
		JsonNode rejected = call(admin, "POST", ITEMS + "/" + id + "/archive", null, 400);
		assertThat(error(rejected)).isEqualTo("AUDIT_REASON_REQUIRED");
		assertThat(rejected.path("data").path("rule").asText()).isEqualTo("ARCHIVE");
		assertThat(status(id)).isEqualTo("PUBLISHED");

		call(admin, "POST", ITEMS + "/" + id + "/archive", Map.of("reason", "Hết mùa, ngừng bán vĩnh viễn."), 200);
		JsonNode row = rows("targetType=SHOP_ITEM&targetId=" + id).get(0);
		assertThat(row.path("action").asText()).isEqualTo("SHOP_ITEM_ARCHIVED");
		assertThat(row.path("changes").findValuesAsText("field")).containsExactly("status", "purchasable");
		assertThat(rows("kind=LIFECYCLE&targetType=SHOP_ITEM&targetId=" + id)).hasSize(3);
	}

	@Test
	void failedAuditRollsTheWriteBack() throws Exception { // T-01, AC-AUD-03
		long id = create(frame("FRAME_T", "Khung T", 900), 201);
		jdbc.execute("ALTER TABLE admin_audit_logs ADD CONSTRAINT chk_it_block_shop CHECK (action <> 'SHOP_ITEM_UPDATED')");
		try {
			assertThat(error(patch(id, Map.of("version", version(id), "price", 950), 500))).isEqualTo("INTERNAL_ERROR");
		} finally {
			jdbc.execute("ALTER TABLE admin_audit_logs DROP CHECK chk_it_block_shop");
		}
		assertThat(price(id)).isEqualTo(900);
	}

	// ---- Missions (M-01…M-03) ----

	@Test
	void dailyRewardPastTheCapNeedsAReasonAndToggleIsLifecycle() throws Exception {
		seeder.run(null);
		long add5 = templateRepository.findByCode("ADD_5_WORDS").orElseThrow().getId();

		JsonNode rejected = patchTemplate(add5, Map.of("rewardCoin", 40), 400); // M-02
		assertThat(error(rejected)).isEqualTo("AUDIT_REASON_REQUIRED");
		String guardrail = "Thưởng mỗi ngày lên 85–100 Coin, vượt mức 75 Coin/ngày đã duyệt.";
		assertThat(rejected.path("data").path("rule").asText()).isEqualTo("DAILY_REWARD_OVER_CAP");
		assertThat(rejected.path("data").path("guardrail").asText()).isEqualTo(guardrail);
		assertThat(templateRepository.findById(add5).orElseThrow().getRewardCoin()).isEqualTo(15);

		patchTemplate(add5, Map.of("rewardCoin", 40, "reason", "Thử thưởng cao hơn cho tuần ra mắt."), 200);
		JsonNode raised = rows("targetType=MISSION_TEMPLATE&targetId=" + add5).get(0);
		assertThat(raised.path("action").asText()).isEqualTo("MISSION_TEMPLATE_UPDATED");
		assertThat(raised.path("guardrail").asText()).isEqualTo(guardrail);

		patchTemplate(add5, Map.of("rewardCoin", 15), 200); // M-03: the ceiling goes down
		assertThat(rows("targetType=MISSION_TEMPLATE&targetId=" + add5).get(0).path("guardrail").isNull()).isTrue();

		long add3 = templateRepository.findByCode("ADD_3_WORDS").orElseThrow().getId();
		patchTemplate(add3, Map.of("isActive", true), 200); // M-01
		JsonNode enabled = rows("kind=LIFECYCLE&targetType=MISSION_TEMPLATE&targetId=" + add3).get(0);
		assertThat(enabled.path("action").asText()).isEqualTo("MISSION_TEMPLATE_ENABLED");
		assertThat(enabled.path("changes").findValuesAsText("field")).containsExactly("isActive");
		patchTemplate(add3, Map.of("isActive", false), 200);
		assertThat(rows("targetType=MISSION_TEMPLATE&targetId=" + add3).get(0).path("action").asText())
				.isEqualTo("MISSION_TEMPLATE_DISABLED");
		assertThat(rows("kind=PRICE_REWARD&area=MISSIONS")).hasSize(2);
	}

	// ---- system content (C-01, C-02, AC-AUD-08) ----

	@Test
	void systemContentIsAuditedALearnersOwnIsNot() throws Exception {
		long system = call(admin, "POST", "/api/collections",
				Map.of("name", "IT Audit " + UUID.randomUUID(), "translation", "Kiểm thử", "type", "SYSTEM"), 200)
				.path("data").path("id").asLong();
		long own = call(user, "POST", "/api/collections", Map.of("name", "Của tôi " + UUID.randomUUID()), 200)
				.path("data").path("id").asLong();
		assertThat(rows("targetType=COLLECTION&targetId=" + system).get(0).path("action").asText())
				.isEqualTo("COLLECTION_CREATED");
		assertThat(rows("targetType=COLLECTION&targetId=" + own)).isEmpty();

		long topic = call(admin, "POST", "/api/collections/" + system + "/topics", Map.of("name", "Đồ bếp"), 200)
				.path("data").path("id").asLong();
		JsonNode created = rows("targetType=TOPIC&targetId=" + topic).get(0);
		assertThat(created.path("action").asText()).isEqualTo("TOPIC_CREATED");
		assertThat(created.path("target").path("parents")).hasSize(1);
		assertThat(created.path("target").path("parents").get(0).asLong()).isEqualTo(system);

		Map<String, Object> word = Map.of("groups", Map.of("main", Map.of("word", "kettle")));
		call(admin, "POST", "/api/topics/" + topic + "/items", word, 201);
		JsonNode added = rows("targetType=TOPIC&targetId=" + topic).get(0);
		assertThat(added.path("action").asText()).isEqualTo("TOPIC_ITEM_ADDED");
		assertThat(added.path("changes").get(0).path("field").asText()).isEqualTo("item");
		assertThat(added.path("changes").get(0).path("after").asText()).isEqualTo("kettle");

		long ownTopic = call(user, "POST", "/api/collections/" + own + "/topics", Map.of("name", "Riêng"), 200)
				.path("data").path("id").asLong();
		call(user, "POST", "/api/topics/" + ownTopic + "/items", word, 201);
		assertThat(rows("targetType=TOPIC&targetId=" + ownTopic)).isEmpty();

		call(admin, "DELETE", "/api/collections/" + system, null, 204);
		call(user, "DELETE", "/api/collections/" + own, null, 204);
		JsonNode deleted = rows("targetType=COLLECTION&targetId=" + system).get(0);
		assertThat(deleted.path("action").asText()).isEqualTo("COLLECTION_DELETED");
		assertThat(deleted.path("changes").findValuesAsText("field")).containsExactly("name", "topicCount", "itemCount");
		assertThat(deleted.path("changes").get(1).path("before").asInt()).isOne();
		assertThat(deleted.path("changes").get(2).path("before").asInt()).isOne();
		assertThat(auditCount()).isEqualTo(4);
	}

	// ---- read API (Q-01…Q-05) ----

	@Test
	void filtersNarrowTheRowsButNotTheSummary() throws Exception { // Q-01, Q-03, AC-AUD-09/11/12
		long gold = create(frame("FRAME_GOLD", "Bậc Thầy Hoàng Kim", 900), 201);
		long other = create(frame("FRAME_OTHER", "Khung Khác", 700), 201);
		patch(gold, Map.of("version", version(gold), "price", 950), 200);
		insert("2026-01-01T00:00:00Z", LocalDate.now(ProgressService.ZONE).minusDays(3), null, "MISSIONS",
				"MISSION_TEMPLATE_CREATED", "MISSION_TEMPLATE", "99");

		JsonNode all = call(admin, "GET", LOG, null, 200).path("data");
		JsonNode summary = all.path("summary");
		assertThat(all.path("meta").path("total").asLong()).isEqualTo(4);
		assertThat(summary.path("total").asLong()).isEqualTo(4);
		assertThat(summary.path("byKind").fieldNames()).toIterable()
				.containsExactly("PRICE_REWARD", "LIFECYCLE", "LEARNER_ACCOUNT", "GUARDRAIL");
		assertThat(summary.path("byKind").path("PRICE_REWARD").asLong()).isEqualTo(1);
		assertThat(summary.path("byKind").path("LIFECYCLE").asLong()).isEqualTo(3);
		assertThat(summary.path("daily")).hasSize(30);
		assertThat(summary.path("daily").findValues("count").stream().mapToLong(JsonNode::asLong).sum()).isEqualTo(4);
		assertThat(summary.path("areas").get(0).path("area").asText()).isEqualTo("SHOP");
		assertThat(summary.path("areas").get(0).path("count").asLong()).isEqualTo(3);
		assertThat(summary.path("actors")).hasSize(2);
		assertThat(summary.path("actors").get(0).path("actor").path("id").asLong()).isEqualTo(adminUser.getId());
		assertThat(summary.path("actors").get(1).path("actor").isNull()).isTrue();

		JsonNode priceOnly = call(admin, "GET", LOG + "?kind=PRICE_REWARD", null, 200).path("data");
		assertThat(priceOnly.path("meta").path("total").asLong()).isEqualTo(1);
		assertThat(priceOnly.path("summary")).isEqualTo(summary);
		assertThat(rows("area=MISSIONS")).hasSize(1);
		assertThat(rows("actor=SYSTEM")).hasSize(1);
		assertThat(rows("actor=" + adminUser.getId())).hasSize(3);
		assertThat(rows("q=hoang%20kim")).hasSize(2);
		assertThat(rows("q=" + adminUser.getEmail().substring(0, 12))).hasSize(3);
		JsonNode history = call(admin, "GET", LOG + "?days=90&targetType=SHOP_ITEM&targetId=" + other, null, 200)
				.path("data");
		assertThat(history.path("data")).hasSize(1);
		assertThat(history.path("summary").path("total").asLong()).isEqualTo(1);
		assertThat(history.path("summary").path("daily")).hasSize(90);
		assertThat(call(admin, "GET", LOG + "?size=1&page=1", null, 200).path("data").path("meta").path("pages").asLong())
				.isEqualTo(4);
	}

	@Test
	void dayFilterUsesTheVietnamDay() throws Exception { // Q-02
		LocalDate today = LocalDate.now(ProgressService.ZONE);
		insert(today.minusDays(1) + "T16:59:59Z", today.minusDays(1), null, "SHOP", "SHOP_ITEM_UPDATED", "SHOP_ITEM", "1");
		insert(today.minusDays(1) + "T17:00:00Z", today, null, "SHOP", "SHOP_ITEM_UPDATED", "SHOP_ITEM", "2");
		JsonNode day = call(admin, "GET", LOG + "?day=" + today, null, 200).path("data");
		assertThat(day.path("data")).hasSize(1);
		assertThat(day.path("data").get(0).path("target").path("id").asText()).isEqualTo("2");
		assertThat(day.path("summary").path("total").asLong()).isEqualTo(2);
	}

	@Test
	void invalidFiltersAndAccess() throws Exception { // Q-04, Q-05
		for (String q : List.of("days=14", "day=2020-01-01", "size=101", "actor=abc", "targetType=SHOP_ITEM")) {
			JsonNode resp = call(admin, "GET", LOG + "?" + q, null, 400);
			assertThat(error(resp)).as(q).isEqualTo("AUDIT_FILTER_INVALID");
		}
		assertThat(call(admin, "GET", LOG + "?days=14", null, 400).path("data").path("errors").get(0).path("field")
				.asText()).isEqualTo("days");
		assertThat(error(call(admin, "GET", LOG + "?area=NOPE", null, 400))).isEqualTo("Invalid parameter: area");
		call(null, "GET", LOG, null, 401);
		call(user, "GET", LOG, null, 403);
	}

	// ---- helpers ----

	private Map<String, Object> frame(String code, String name, long price) {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("type", "AVATAR_FRAME");
		body.put("name", name);
		body.put("iconKey", "public/shop/icon-1.png");
		body.put("frameAssetKey", "public/shop/frame-1.webp");
		body.put("purchasable", true);
		body.put("price", price);
		return body;
	}

	private long create(Map<String, Object> body, int expected) throws Exception {
		return call(admin, "POST", ITEMS, body, expected).path("data").path("id").asLong();
	}

	private JsonNode publish(long id, String reason, int expected) throws Exception {
		return call(admin, "POST", ITEMS + "/" + id + "/publish", reason == null ? null : Map.of("reason", reason),
				expected);
	}

	private JsonNode patch(long id, Map<String, Object> body, int expected) throws Exception {
		return call(admin, "PATCH", ITEMS + "/" + id, body, expected);
	}

	private JsonNode patchTemplate(long id, Map<String, Object> fields, int expected) throws Exception {
		Map<String, Object> body = new HashMap<>(fields);
		body.put("version", templateRepository.findById(id).orElseThrow().getVersion());
		return call(admin, "PATCH", TEMPLATES + "/" + id, body, expected);
	}

	private JsonNode item(long id) throws Exception {
		return call(admin, "GET", ITEMS + "/" + id, null, 200).path("data");
	}

	private long version(long id) throws Exception {
		return item(id).path("version").asLong();
	}

	private long price(long id) throws Exception {
		return item(id).path("price").asLong();
	}

	private String status(long id) throws Exception {
		return item(id).path("status").asText();
	}

	/** Rows of GET /api/admin/audit-log, newest first. */
	private JsonNode rows(String query) throws Exception {
		return call(admin, "GET", LOG + "?" + query, null, 200).path("data").path("data");
	}

	private int auditCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs", Integer.class);
	}

	/** A row the API cannot produce (a chosen instant, the system as actor). */
	private void insert(String at, LocalDate vnDay, Long actorId, String area, String action, String targetType,
			String targetId) {
		jdbc.update("INSERT INTO admin_audit_logs (created_at, vn_day, actor_id, area, action, target_type, target_id,"
				+ " target_name, changes, economy_change, search_text) VALUES (?, ?, ?, ?, ?, ?, ?, 'Seeded', '[]', 0,"
				+ " 'seeded')", java.sql.Timestamp.from(Instant.parse(at)), vnDay, actorId, area, action, targetType,
				targetId);
	}

	private static String error(JsonNode resp) {
		return resp.path("error").asText();
	}

	private JsonNode call(String token, String method, String path, Object body, int expected) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.header("Content-Type", "application/json");
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).withFailMessage("%s %s → %s %s", method, path, resp.statusCode(), resp.body())
				.isEqualTo(expected);
		return resp.body().isEmpty() ? json.nullNode() : json.readTree(resp.body());
	}

	private User saveUser() {
		return userRepository.save(User.builder()
				.email("audit-it-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Tester")
				.activated(true)
				.coin(0L)
				.build());
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
