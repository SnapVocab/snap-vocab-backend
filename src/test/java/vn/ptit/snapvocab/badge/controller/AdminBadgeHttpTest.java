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
		jdbc.update("DELETE FROM users WHERE email LIKE 'admin-bdg-it-%'");
	}

	@Test
	void listAndDetailRequireAdmin() throws Exception {
		call(null, "GET", BASE, 401);
		call(learner, "GET", BASE, 403);
		call(learner, "GET", BASE + "/" + inactive.getId(), 403);
		call(admin, "GET", BASE, 200);
	}

	@Test
	void listIncludesInactiveCountsZerosNullVersionAndStableServerOrder() throws Exception {
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
		assertThat(items.get(0).has("version")).isTrue();
		assertThat(items.get(0).get("version").isNull()).isTrue();
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
		assertThat(item.get("version").isNull()).isTrue();
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
	void p1DoesNotExposeWriteEndpoints() throws Exception {
		for (String method : List.of("POST", "PATCH", "PUT", "DELETE")) {
			call(admin, method, method.equals("POST") ? BASE : BASE + "/" + active.getId(), 405);
		}
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
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.method(method, HttpRequest.BodyPublishers.noBody());
		if (token != null) request.header("Authorization", "Bearer " + token);
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
		return response;
	}
}
