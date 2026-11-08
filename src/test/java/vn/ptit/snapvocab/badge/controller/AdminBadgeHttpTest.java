package vn.ptit.snapvocab.badge.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** P1 through real security, response envelope and aggregate queries against the isolated LOCAL test DB. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminBadgeHttpTest {

	private static final String BASE = "/api/admin/badges";
	@MockitoBean MinioClient minioClient;
	@MockitoBean RedissonClient redissonClient;
	@MockitoBean CacheManager cacheManager;
	@LocalServerPort int port;
	@Autowired JwtEncoder jwtEncoder;
	@Autowired UserRepository users;
	@Autowired BadgeRepository badges;
	@Autowired ShopItemRepository shopItems;
	@Autowired JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private String admin;
	private String learner;
	private User learnerUser;
	private Badge inactive;
	private Badge active;

	@BeforeEach
	void setUp() {
		cleanUp();
		admin = bearer(user(), "ROLE_USER ROLE_ADMIN");
		learnerUser = user();
		learner = bearer(learnerUser, "ROLE_USER");
		inactive = badge(500_001, 0, false);
		active = badge(500_002, 0, true);
		// Both seen and unseen earners count, regardless of account activation.
		jdbc.update("INSERT INTO user_badges (user_id, badge_id, created_at, seen_at) VALUES (?, ?, NOW(6), NULL)",
				learnerUser.getId(), inactive.getId());
		User other = user();
		jdbc.update("UPDATE users SET activated = false WHERE id = ?", other.getId());
		jdbc.update("INSERT INTO user_badges (user_id, badge_id, created_at, seen_at) VALUES (?, ?, NOW(6), NOW(6))",
				other.getId(), inactive.getId());
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM user_badges WHERE badge_id IN (SELECT id FROM badges WHERE code LIKE 'ADMIN_BDG_IT_%')");
		jdbc.update("DELETE FROM badges WHERE code LIKE 'ADMIN_BDG_IT_%'");
		jdbc.update("DELETE FROM admin_audit_logs WHERE area = 'BADGES'");
		jdbc.update("DELETE FROM users WHERE email LIKE 'admin-bdg-it-%'");
		jdbc.update("DELETE FROM shop_items WHERE code LIKE 'ADMIN_BDG_IT_%'");
	}

	@Test
	void listAndDetailRequireAdmin() throws Exception {
		call(null, "GET", BASE, 401);
		call(learner, "GET", BASE, 403);
		call(learner, "GET", BASE + "/" + inactive.getId(), 403);
		call(admin, "GET", BASE, 200);
	}

	@Test
	void listIncludesInactiveCountsZerosVersionAndStableServerOrder() throws Exception {
		HttpResponse<String> response = call(admin, "GET", BASE, 200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = json.readTree(response.body());
		assertThat(body.path("statusCode").asInt()).isEqualTo(200);
		JsonNode items = body.path("data");
		assertThat(items.isArray()).isTrue();
		assertThat(items.get(0).path("id").asLong()).isEqualTo(inactive.getId());
		assertThat(items.get(1).path("id").asLong()).isEqualTo(active.getId());
		assertThat(items.get(0).path("isActive").asBoolean()).isFalse();
		assertThat(items.get(0).path("earnerCount").asLong()).isEqualTo(2);
		assertThat(items.get(1).path("earnerCount").asLong()).isZero();
		assertThat(items.get(0).path("version").asLong()).isZero();
		assertThat(items.get(0).path("createdAt").asText()).isNotBlank();
		assertThat(items.get(0).path("updatedAt").asText()).isNotBlank();
	}

	@Test
	void detailIncludesInactiveBadgeAndMissingIdUsesTheContractCode() throws Exception {
		JsonNode item = json.readTree(call(admin, "GET", BASE + "/" + inactive.getId(), 200).body()).path("data");
		assertThat(item.path("code").asText()).isEqualTo(inactive.getCode());
		assertThat(item.path("description").asText()).isEqualTo(inactive.getDescription());
		assertThat(item.path("isActive").asBoolean()).isFalse();
		assertThat(item.path("earnerCount").asLong()).isEqualTo(2);
		assertThat(item.path("version").asLong()).isZero();
		assertThat(json.readTree(call(admin, "GET", BASE + "/" + Long.MAX_VALUE, 404).body()).path("error").asText())
				.isEqualTo("BADGE_NOT_FOUND");
	}

	@Test
	void adminReadsNeverGrantMarkSeenOrEditCatalog() throws Exception {
		List<Map<String, Object>> grants = jdbc.queryForList("SELECT * FROM user_badges ORDER BY id");
		List<Map<String, Object>> catalog = jdbc.queryForList("SELECT * FROM badges ORDER BY id");
		for (int i = 0; i < 2; i++) {
			call(admin, "GET", BASE, 200);
			call(admin, "GET", BASE + "/" + active.getId(), 200);
		}
		assertThat(jdbc.queryForList("SELECT * FROM user_badges ORDER BY id")).isEqualTo(grants);
		assertThat(jdbc.queryForList("SELECT * FROM badges ORDER BY id")).isEqualTo(catalog);
	}

	@Test
	void noCreateReplaceOrDeleteBeforeP3() throws Exception {
		for (String method : List.of("POST", "PUT", "DELETE")) {
			call(admin, method, method.equals("POST") ? BASE : BASE + "/" + active.getId(), 405);
		}
		call(learner, "PATCH", BASE + "/" + active.getId(), Map.of("version", 0, "name", "X"), 403);
	}

	// ---- P2 (badges.md §12.5) ----

	@Test
	void editingContentKeepsEveryEarnerAndIsAudited() throws Exception {
		List<Map<String, Object>> grants = jdbc.queryForList("SELECT * FROM user_badges ORDER BY id");
		JsonNode edited = data(call(admin, "PATCH", BASE + "/" + inactive.getId(),
				Map.of("version", 0, "name", "  Tên mới  ", "iconKey", "award", "sortOrder", 7), 200));
		assertThat(edited.path("name").asText()).isEqualTo("Tên mới");
		assertThat(edited.path("iconKey").asText()).isEqualTo("award");
		assertThat(edited.path("version").asLong()).isOne();
		assertThat(edited.path("earnerCount").asLong()).isEqualTo(2);
		// PD-BDG-13b: earned date and seen state untouched, no new grant.
		assertThat(jdbc.queryForList("SELECT * FROM user_badges ORDER BY id")).isEqualTo(grants);
		Map<String, Object> row = jdbc.queryForMap("SELECT action, target_code, changes, object_version FROM admin_audit_logs"
				+ " WHERE area = 'BADGES'");
		assertThat(row).containsEntry("action", "BADGE_UPDATED").containsEntry("target_code", inactive.getCode())
				.containsEntry("object_version", 1L);
		assertThat((String) row.get("changes")).contains("\"name\"", "\"iconKey\"", "\"sortOrder\"").doesNotContain("isActive");

		// Same values again: nothing saved, nothing logged.
		call(admin, "PATCH", BASE + "/" + inactive.getId(), Map.of("version", 1, "name", "Tên mới"), 200);
		assertThat(badges.findById(inactive.getId()).orElseThrow().getVersion()).isOne();
		assertThat(auditRows()).isOne();
	}

	@Test
	void switchingOnAndOffIsLifecycleAndCreatesNoGrant() throws Exception {
		long grants = jdbc.queryForObject("SELECT COUNT(*) FROM user_badges", Long.class);
		call(admin, "PATCH", BASE + "/" + inactive.getId(), Map.of("version", 0, "isActive", true), 200);
		call(admin, "PATCH", BASE + "/" + inactive.getId(), Map.of("version", 1, "isActive", false), 200);
		assertThat(jdbc.queryForList("SELECT action FROM admin_audit_logs WHERE area = 'BADGES' ORDER BY id", String.class))
				.containsExactly("BADGE_ENABLED", "BADGE_DISABLED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_badges", Long.class)).isEqualTo(grants);
	}

	@Test
	void lockedFieldsStaleVersionAndUnknownIconAreRejectedWithoutAWrite() throws Exception {
		String path = BASE + "/" + active.getId();
		JsonNode locked = json.readTree(call(admin, "PATCH", path, Map.of("version", 0, "threshold", 1), 409).body());
		assertThat(locked.path("error").asText()).isEqualTo("FIELD_LOCKED");
		assertThat(locked.path("data").path("fields").get(0).asText()).isEqualTo("threshold");

		call(admin, "PATCH", path, Map.of("version", 0, "name", "Một"), 200);
		// Two admins read version 0; the one who saves second gets the current version back.
		JsonNode stale = json.readTree(call(admin, "PATCH", path, Map.of("version", 0, "name", "Hai"), 409).body());
		assertThat(stale.path("error").asText()).isEqualTo("VERSION_CONFLICT");
		assertThat(stale.path("data").path("currentVersion").asLong()).isOne();

		JsonNode icon = json.readTree(call(admin, "PATCH", path, Map.of("version", 1, "iconKey", "rocket"), 400).body());
		assertThat(icon.path("error").asText()).isEqualTo("VALIDATION_ERROR");
		assertThat(icon.path("data").path("errors").get(0).path("field").asText()).isEqualTo("iconKey");
		for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("version", 1, "name", "   "),
				Map.of("version", 1, "description", "x".repeat(256)), Map.of("version", 1, "sortOrder", "1"),
				Map.of("version", 1, "isActive", "yes"), Map.of("version", 1, "seenAt", "now"), Map.of("name", "No version"))) {
			assertThat(json.readTree(call(admin, "PATCH", path, bad, 400).body()).path("error").asText())
					.as(bad.toString()).isEqualTo("VALIDATION_ERROR");
		}
		assertThat(badges.findById(active.getId()).orElseThrow().getName()).isEqualTo("Một");
		assertThat(call(admin, "PATCH", BASE + "/" + Long.MAX_VALUE, Map.of("version", 0, "name", "X"), 404).body())
				.contains("BADGE_NOT_FOUND");
	}

	@Test
	void rewardsAreEditableAuditedAndTheItemMustBeAnUnsoldPublishedFrame() throws Exception { // PD-BDG-14
		String path = BASE + "/" + active.getId();
		ShopItem sold = rewardFrame(true, ShopItemStatus.PUBLISHED, ShopItemType.CARD_FRAME);
		ShopItem draft = rewardFrame(false, ShopItemStatus.DRAFT, ShopItemType.CARD_FRAME);
		ShopItem booster = rewardFrame(false, ShopItemStatus.PUBLISHED, ShopItemType.XP_BOOSTER);
		ShopItem frame = rewardFrame(false, ShopItemStatus.PUBLISHED, ShopItemType.CARD_FRAME);
		for (String code : List.of(sold.getCode(), draft.getCode(), booster.getCode(), "NO_SUCH_ITEM")) {
			assertThat(json.readTree(call(admin, "PATCH", path, Map.of("version", 0, "rewardItemCode", code), 400).body())
					.path("error").asText()).as(code).isEqualTo("REWARD_ITEM_NOT_ELIGIBLE");
		}
		for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("version", 0, "rewardCoin", -1),
				Map.of("version", 0, "rewardXp", 1001), Map.of("version", 0, "rewardCoin", "5"))) {
			assertThat(json.readTree(call(admin, "PATCH", path, bad, 400).body()).path("error").asText())
					.as(bad.toString()).isEqualTo("VALIDATION_ERROR");
		}

		JsonNode saved = data(call(admin, "PATCH", path,
				Map.of("version", 0, "rewardCoin", 0, "rewardXp", 300, "rewardItemCode", frame.getCode()), 200));
		assertThat(saved.path("rewardXp").asInt()).isEqualTo(300);
		assertThat(saved.path("rewardItemCode").asText()).isEqualTo(frame.getCode());
		assertThat(saved.path("rewardItem").path("type").asText()).isEqualTo("CARD_FRAME");
		assertThat(saved.path("rewardItem").path("purchasable").asBoolean()).isFalse();
		assertThat(jdbc.queryForObject("SELECT changes FROM admin_audit_logs WHERE area = 'BADGES'", String.class))
				.contains("\"rewardXp\"", "\"rewardItemCode\"");
		JsonNode listed = data(call(admin, "GET", BASE, 200));
		assertThat(listed.findValues("rewardItemCode").stream().map(JsonNode::asText)).contains(frame.getCode());

		JsonNode cleared = data(call(admin, "PATCH", path, json.readTree("{\"version\":1,\"rewardItemCode\":null}"), 200));
		assertThat(cleared.path("rewardItemCode").isNull()).isTrue();
		assertThat(cleared.path("rewardItem").isNull()).isTrue();
	}

	@Test
	void refillEstimateCountsActivatedQualifiedAccountsThatDoNotHoldTheBadge() throws Exception { // PD-BDG-13d
		Badge streak = badges.saveAndFlush(Badge.builder().code("ADMIN_BDG_IT_STREAK_" + UUID.randomUUID().toString()
				.replace("-", "").substring(0, 12)).name("Streak").description("Long streak")
				.metric(BadgeMetric.LONGEST_STREAK_DAYS).threshold(400_001).iconKey("flame").sortOrder(0).active(false).build());
		User qualified = user();
		User holder = user();
		User inactiveAccount = user();
		for (User u : List.of(qualified, holder, inactiveAccount)) {
			jdbc.update("UPDATE users SET longest_streak_days = 400001 WHERE id = ?", u.getId());
		}
		jdbc.update("UPDATE users SET activated = false WHERE id = ?", inactiveAccount.getId());
		jdbc.update("INSERT INTO user_badges (user_id, badge_id, created_at) VALUES (?, ?, NOW(6))", holder.getId(), streak.getId());

		JsonNode estimate = data(call(admin, "GET", BASE + "/" + streak.getId() + "/refill-estimate", 200));
		assertThat(estimate.path("eligibleCount").asLong()).isOne();
		assertThat(estimate.path("version").asLong()).isZero();
		assertThat(estimate.path("calculatedAt").asText()).isNotBlank();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_badges WHERE badge_id = ?", Long.class, streak.getId()))
				.isOne(); // read-only
		assertThat(data(call(admin, "GET", BASE + "/" + active.getId() + "/refill-estimate", 200))
				.path("eligibleCount").asLong()).isZero();
	}

	private ShopItem rewardFrame(boolean purchasable, ShopItemStatus status, ShopItemType type) {
		return shopItems.save(ShopItem.builder().code("ADMIN_BDG_IT_ITEM_" + UUID.randomUUID().toString().replace("-", "")
				.substring(0, 12).toUpperCase()).type(type).name("Khung").status(status).purchasable(purchasable)
				.price(purchasable ? 500L : null).build());
	}

	private long auditRows() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE area = 'BADGES'", Long.class);
	}

	private JsonNode data(HttpResponse<String> response) throws Exception {
		return json.readTree(response.body()).path("data");
	}

	private Badge badge(int threshold, int sortOrder, boolean active) {
		return badges.saveAndFlush(Badge.builder().code("ADMIN_BDG_IT_" + UUID.randomUUID().toString().replace("-", ""))
				.name("Admin badge").description("Read-only test").metric(BadgeMetric.QUIZ_COMPLETED)
				.threshold(threshold).iconKey("clipboard-check").sortOrder(sortOrder).active(active).build());
	}

	private User user() {
		return users.save(User.builder().email("admin-bdg-it-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60)).firstName("Tester").activated(true).build());
	}

	private String bearer(User user, String authorities) {
		JwtClaimsSet claims = JwtClaimsSet.builder().issuedAt(Instant.now()).expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
				.subject(user.getEmail()).claim(SecurityUtils.AUTHORITIES_CLAIM, authorities)
				.claim(SecurityUtils.USER_ID_CLAIM, user.getId()).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims)).getTokenValue();
	}

	private HttpResponse<String> call(String token, String method, String path, int status) throws Exception {
		return call(token, method, path, null, status);
	}

	private HttpResponse<String> call(String token, String method, String path, Object body, int status) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Content-Type", "application/json")
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
		if (token != null) request.header("Authorization", "Bearer " + token);
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
		return response;
	}
}
