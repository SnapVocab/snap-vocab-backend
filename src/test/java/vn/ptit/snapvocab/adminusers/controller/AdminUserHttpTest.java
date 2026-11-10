package vn.ptit.snapvocab.adminusers.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** Admin › Người học over HTTP → Security → AdminUserService → LOCAL MySQL; same mocks as AdminAuditHttpTest. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminUserHttpTest {

	private static final String USERS = "/api/admin/users";
	private static final String PASSWORD = "Secret#123";
	private static final String REASON = "Tạo nhiều tài khoản để nhận thưởng nhiệm vụ.";

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
	PasswordEncoder passwordEncoder;
	@Autowired
	JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private final String tag = "learners-it-" + UUID.randomUUID().toString().substring(0, 8);
	private String admin;

	@BeforeEach
	void setUp() {
		cleanUp();
		jdbc.update("INSERT IGNORE INTO authorities (name) VALUES ('ROLE_ADMIN')");
		User adminUser = saveUser("admin", true);
		jdbc.update("INSERT INTO user_authorities (user_id, authority_name) VALUES (?, 'ROLE_ADMIN')", adminUser.getId());
		admin = bearer(adminUser, "ROLE_USER ROLE_ADMIN");
		when(redissonClient.getKeys()).thenReturn(mock(RKeys.class)); // revokeAllTokens: no stored tokens
	}

	@AfterEach
	void cleanUp() {
		String ours = "SELECT id FROM users WHERE email LIKE 'learners-it-%'";
		jdbc.update("DELETE FROM learning_events WHERE user_id IN (" + ours + ")");
		jdbc.update("DELETE FROM admin_audit_logs WHERE target_type = 'LEARNER'");
		jdbc.update("DELETE FROM user_authorities WHERE user_id IN (SELECT id FROM (" + ours + ") u)");
		jdbc.update("DELETE FROM users WHERE email LIKE 'learners-it-%'");
	}

	@Test
	void listFiltersLearnersAndSkipsAdmins() throws Exception {
		JsonNode before = call(admin, "GET", USERS, null, 200).path("data").path("summary");
		User active = saveUser("an", true);
		saveUser("binh", false);
		studiedToday(active);

		JsonNode all = call(admin, "GET", USERS + "?q=" + tag + "&size=50", null, 200).path("data");
		assertThat(all.path("data")).hasSize(2); // the admin shares the tag but is no learner
		assertThat(all.path("meta").path("total").asLong()).isEqualTo(2);
		JsonNode first = all.path("data").get(0); // sort=lastStudied: who studied comes first
		assertThat(first.path("id").asLong()).isEqualTo(active.getId());
		assertThat(first.path("status").asText()).isEqualTo("ACTIVE");
		assertThat(first.path("displayName").asText()).isEqualTo("an Tester");
		assertThat(first.path("lastStudiedAt").asText()).endsWith("Z");

		JsonNode pending = call(admin, "GET", USERS + "?q=" + tag + "&status=PENDING", null, 200).path("data");
		assertThat(pending.path("data")).extracting(n -> n.path("email").asText()).containsExactly(email("binh"));
		JsonNode never = call(admin, "GET", USERS + "?q=" + tag + "&activity=NEVER", null, 200).path("data");
		assertThat(never.path("data")).extracting(n -> n.path("email").asText()).containsExactly(email("binh"));

		JsonNode after = call(admin, "GET", USERS, null, 200).path("data").path("summary");
		assertThat(after.path("total").asLong() - before.path("total").asLong()).isEqualTo(2);
		assertThat(after.path("pending").asLong() - before.path("pending").asLong()).isEqualTo(1);
		assertThat(after.path("active7d").asLong() - before.path("active7d").asLong()).isEqualTo(1);

		call(admin, "GET", USERS + "?status=NOPE", null, 400);
		call(admin, "GET", USERS + "?sort=email", null, 400);
		call(bearer(active, "ROLE_USER"), "GET", USERS, null, 403);
	}

	@Test
	void profileShowsTheLearnersActivity() throws Exception {
		User learner = saveUser("chi", true);
		studiedToday(learner);

		JsonNode d = call(admin, "GET", USERS + "/" + learner.getId(), null, 200).path("data");
		assertThat(d.path("id").asLong()).isEqualTo(learner.getId()); // row fields unwrapped at the top level
		assertThat(d.path("ban").isNull()).isTrue();
		assertThat(d.path("activity30d")).hasSize(30);
		assertThat(d.path("activity30d").get(29).asLong()).isEqualTo(1); // today, last
		assertThat(d.path("progress").path("quizAccuracy").isNull()).isTrue();
		assertThat(d.path("missions7d").path("assigned").asLong()).isZero();
		assertThat(d.path("timeline")).extracting(n -> n.path("kind").asText()).contains("STUDY", "ACCOUNT");

		User adminAccount = userRepository.findOneByEmailIgnoreCase(email("admin")).orElseThrow();
		call(admin, "GET", USERS + "/" + adminAccount.getId(), null, 404);
		call(admin, "GET", USERS + "/0", null, 404);
	}

	@Test
	void banNeedsAReasonBlocksSignInAndIsAudited() throws Exception {
		User learner = saveUser("dung", true);
		String path = USERS + "/" + learner.getId();
		assertThat(login(learner).statusCode()).isNotEqualTo(403); // signs in before the ban (Redis is mocked)

		assertThat(call(admin, "POST", path + "/ban", Map.of(), 400).path("error").asText())
				.isEqualTo("AUDIT_REASON_REQUIRED");
		call(admin, "POST", path + "/ban", Map.of("reason", "ngắn"), 400);

		JsonNode banned = call(admin, "POST", path + "/ban", Map.of("reason", REASON), 200).path("data");
		assertThat(banned.path("status").asText()).isEqualTo("BANNED");
		assertThat(banned.path("ban").path("reason").asText()).isEqualTo(REASON);
		assertThat(banned.path("ban").path("by").asText()).isEqualTo(email("admin"));
		call(admin, "POST", path + "/ban", Map.of("reason", REASON), 409);

		HttpResponse<String> refused = login(learner);
		assertThat(refused.statusCode()).isEqualTo(403);
		assertThat(json.readTree(refused.body()).path("error").asText()).isEqualTo("USER_BANNED");

		JsonNode log = call(admin, "GET", "/api/admin/audit-log?targetType=LEARNER&targetId=" + learner.getId(), null,
				200).path("data").path("data");
		assertThat(log).hasSize(1);
		assertThat(log.get(0).path("action").asText()).isEqualTo("LEARNER_BANNED");
		assertThat(log.get(0).path("reason").asText()).isEqualTo(REASON);

		JsonNode unbanned = call(admin, "POST", path + "/unban", null, 200).path("data");
		assertThat(unbanned.path("status").asText()).isEqualTo("ACTIVE");
		assertThat(unbanned.path("ban").isNull()).isTrue();
		assertThat(login(learner).statusCode()).isNotEqualTo(403);
		call(admin, "POST", path + "/unban", null, 409);
	}

	private void studiedToday(User u) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		jdbc.update("UPDATE users SET last_studied_at = ? WHERE id = ?", java.sql.Timestamp.from(now), u.getId());
		jdbc.update("INSERT INTO learning_events (user_id, event_type, learning_date, source_key, created_at)"
				+ " VALUES (?, 'SRS_REVIEW_COMPLETED', ?, ?, ?)", u.getId(), StreakFreezeService.today(),
				"it-lu-" + UUID.randomUUID(), java.sql.Timestamp.from(now));
	}

	private HttpResponse<String> login(User u) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
						Map.of("email", u.getEmail(), "password", PASSWORD))))
				.header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString());
	}

	private JsonNode call(String token, String method, String path, Object body, int expected) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.header("Content-Type", "application/json");
		req.header("Authorization", "Bearer " + token);
		HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).withFailMessage("%s %s → %s %s", method, path, resp.statusCode(), resp.body())
				.isEqualTo(expected);
		return resp.body().isEmpty() ? json.nullNode() : json.readTree(resp.body());
	}

	private String email(String who) {
		return tag + "-" + who + "@snapvocab.local";
	}

	private User saveUser(String who, boolean activated) {
		return userRepository.save(User.builder()
				.email(email(who))
				.password(passwordEncoder.encode(PASSWORD))
				.firstName(who)
				.lastName("Tester")
				.activated(activated)
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
