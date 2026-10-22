package vn.ptit.snapvocab.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** HTTP → Security (Bearer JWT) → notification controllers → services → LOCAL MySQL (notification.md §4.3, §6). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class NotificationHttpEndToEndTest {

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;

	@Autowired
	TestRestTemplate rest;
	@Autowired
	JwtEncoder jwtEncoder;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private User lan;
	private User minh;
	private String lanToken;
	private String minhToken;
	private String adminToken;

	@BeforeEach
	void setUp() {
		lan = user("Lan");
		minh = user("Minh");
		lanToken = bearer(lan, "ROLE_USER");
		minhToken = bearer(minh, "ROLE_USER");
		adminToken = bearer(user("Admin"), "ROLE_USER ROLE_ADMIN");
	}

	@AfterEach
	void cleanUp() {
		String ours = "SELECT id FROM notifications WHERE dedupe_key LIKE 'IT:%' OR dedupe_key LIKE 'SYSTEM:it-%'";
		jdbc.update("DELETE FROM user_notifications WHERE notification_id IN (" + ours + ")");
		jdbc.update("DELETE FROM notifications WHERE dedupe_key LIKE 'IT:%' OR dedupe_key LIKE 'SYSTEM:it-%'");
		jdbc.update("DELETE FROM users WHERE email LIKE 'notif-http-%'");
	}

	/** AC-08, AC-09, AC-10. */
	@Test
	void inboxPagesReadsOneAndReadsAll() throws Exception {
		assertThat(rest.getForEntity("/api/me/notifications", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		jdbc.update("""
				INSERT INTO notifications (created_at, title, content, type, dedupe_key)
				WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 45)
				SELECT NOW(6), 'Bảo trì', CONCAT('c', n), 'SYSTEM', CONCAT('IT:', ?, ':', n) FROM seq
				""", lan.getId());
		jdbc.update("INSERT INTO user_notifications (user_id, notification_id, is_read) SELECT ?, id, FALSE "
				+ "FROM notifications WHERE dedupe_key LIKE CONCAT('IT:', ?, ':%')", lan.getId(), lan.getId());

		JsonNode first = data(call(lanToken, HttpMethod.GET, "/api/me/notifications?size=20", null));
		assertThat(first.path("items")).hasSize(20);
		JsonNode top = first.path("items").get(0);
		assertThat(top.path("isRead").asBoolean(true)).isFalse();
		assertThat(top.has("read")).isFalse();
		assertThat(top.path("type").asText()).isEqualTo("SYSTEM");
		assertThat(top.path("title").asText()).isEqualTo("Bảo trì");
		assertThat(top.path("route").isNull()).isTrue();
		assertThat(top.path("createdAt").asText()).endsWith("Z");
		assertThat(top.path("id").asLong()).isGreaterThan(first.path("items").get(1).path("id").asLong());
		long cursor = first.path("nextCursor").asLong();
		assertThat(cursor).isEqualTo(first.path("items").get(19).path("id").asLong());

		JsonNode second = data(call(lanToken, HttpMethod.GET, "/api/me/notifications?cursor=" + cursor, null));
		assertThat(second.path("items")).hasSize(20);
		JsonNode last = data(call(lanToken, HttpMethod.GET,
				"/api/me/notifications?cursor=" + second.path("nextCursor").asLong(), null));
		assertThat(last.path("items")).hasSize(5);
		assertThat(last.path("nextCursor").isNull()).isTrue();
		assertThat(data(call(lanToken, HttpMethod.GET, "/api/me/notifications?size=500", null)).path("items")).hasSize(45);
		assertThat(data(call(lanToken, HttpMethod.GET, "/api/me/notifications?size=0", null)).path("items")).hasSize(1);
		assertThat(data(call(minhToken, HttpMethod.GET, "/api/me/notifications", null)).path("items")).isEmpty();
		assertThat(unread(lanToken)).isEqualTo(45);

		long id = top.path("id").asLong();
		JsonNode read = data(call(lanToken, HttpMethod.PUT, "/api/me/notifications/" + id + "/read", null));
		JsonNode again = data(call(lanToken, HttpMethod.PUT, "/api/me/notifications/" + id + "/read", null));
		assertThat(read.path("isRead").asBoolean()).isTrue();
		assertThat(read.path("readAt").asText()).isNotBlank().isEqualTo(again.path("readAt").asText());
		ResponseEntity<String> foreign = call(minhToken, HttpMethod.PUT, "/api/me/notifications/" + id + "/read", null);
		assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(error(foreign)).isEqualTo("NOTIFICATION_NOT_FOUND");
		assertThat(call(lanToken, HttpMethod.PUT, "/api/me/notifications/abc/read", null).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(unread(lanToken)).isEqualTo(44);

		assertThat(data(call(lanToken, HttpMethod.POST, "/api/me/notifications/read-all", null)).path("updated").asInt())
				.isEqualTo(44);
		assertThat(data(call(lanToken, HttpMethod.POST, "/api/me/notifications/read-all", null)).path("updated").asInt())
				.isZero();
		assertThat(unread(lanToken)).isZero();
	}

	/** AC-18, AC-19 and A5/A6 errors. */
	@Test
	void deviceTokenRegistersMovesAndUnregisters() throws Exception {
		String token = "ExponentPushToken[it" + UUID.randomUUID().toString().replace("-", "") + "]";

		JsonNode registered = data(call(lanToken, HttpMethod.POST, "/api/me/devices",
				Map.of("token", token, "platform", "ANDROID")));
		assertThat(registered.path("platform").asText()).isEqualTo("ANDROID");
		assertThat(registered.path("lastSeenAt").asText()).endsWith("Z");
		assertThat(owner(token)).isEqualTo(lan.getId());

		ResponseEntity<String> badToken = call(lanToken, HttpMethod.POST, "/api/me/devices",
				Map.of("token", "fcm-raw-token", "platform", "ANDROID"));
		assertThat(badToken.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error(badToken)).isEqualTo("INVALID_DEVICE_TOKEN");
		ResponseEntity<String> badPlatform = call(lanToken, HttpMethod.POST, "/api/me/devices",
				Map.of("token", token, "platform", "WEB"));
		assertThat(badPlatform.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error(badPlatform)).isEqualTo("Validation failed");
		assertThat(json.readTree(badPlatform.getBody()).path("data").has("platform")).isTrue();

		assertThat(call(minhToken, HttpMethod.POST, "/api/me/devices", Map.of("token", token, "platform", "IOS"))
				.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(owner(token)).isEqualTo(minh.getId());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_tokens WHERE token = ?", Integer.class, token)).isEqualTo(1);

		assertThat(removed(lanToken, Map.of("token", token))).isFalse();
		assertThat(removed(minhToken, Map.of("token", token))).isTrue();
		assertThat(removed(minhToken, Map.of("token", token))).isFalse();
		assertThat(removed(minhToken, Map.of())).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_tokens WHERE token = ?", Integer.class, token)).isZero();
	}

	/** AC-22, AC-23 and A8 validation. */
	@Test
	void adminCreatesSystemNotificationOnce() throws Exception {
		String key = "it-maint-" + System.nanoTime();
		Map<String, Object> body = Map.of("title", "  Bảo trì hệ thống 20/10  ", "content",
				"SnapVocab bảo trì từ 23:00 đến 23:30 ngày 20/10.", "dedupeKey", key);
		assertThat(call(lanToken, HttpMethod.POST, "/api/admin/notifications", body).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);

		long activated = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE activated = TRUE", Long.class);
		ResponseEntity<String> created = call(adminToken, HttpMethod.POST, "/api/admin/notifications", body);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(json.readTree(created.getBody()).path("statusCode").asInt()).isEqualTo(201);
		assertThat(data(created).path("recipientCount").asLong()).isEqualTo(activated);
		JsonNode inbox = data(call(minhToken, HttpMethod.GET, "/api/me/notifications", null)).path("items");
		assertThat(inbox).hasSize(1);
		assertThat(inbox.get(0).path("title").asText()).isEqualTo("Bảo trì hệ thống 20/10");
		assertThat(inbox.get(0).path("type").asText()).isEqualTo("SYSTEM");

		ResponseEntity<String> duplicate = call(adminToken, HttpMethod.POST, "/api/admin/notifications", body);
		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(error(duplicate)).isEqualTo("DUPLICATE_NOTIFICATION");

		ResponseEntity<String> badRoute = call(adminToken, HttpMethod.POST, "/api/admin/notifications",
				Map.of("title", "t", "content", "c", "route", "/admin", "dedupeKey", key + "-r"));
		assertThat(badRoute.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error(badRoute)).isEqualTo("ROUTE_NOT_ALLOWED");

		ResponseEntity<String> invalid = call(adminToken, HttpMethod.POST, "/api/admin/notifications",
				Map.of("title", "   ", "content", "c", "dedupeKey", "Bad Key"));
		assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		JsonNode fields = json.readTree(invalid.getBody()).path("data");
		assertThat(fields.has("title")).isTrue();
		assertThat(fields.has("dedupeKey")).isTrue();
	}

	// --- helpers ---------------------------------------------------------------------------------

	private ResponseEntity<String> call(String bearer, HttpMethod method, String url, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
		return json.readTree(response.getBody()).path("data");
	}

	private String error(ResponseEntity<String> response) throws Exception {
		return json.readTree(response.getBody()).path("error").asText();
	}

	private long unread(String bearer) throws Exception {
		return data(call(bearer, HttpMethod.GET, "/api/me/notifications/unread-count", null)).path("count").asLong();
	}

	private boolean removed(String bearer, Map<String, String> body) throws Exception {
		return data(call(bearer, HttpMethod.POST, "/api/me/devices/unregister", body)).path("removed").asBoolean();
	}

	private Long owner(String token) {
		return jdbc.queryForObject("SELECT user_id FROM device_tokens WHERE token = ?", Long.class, token);
	}

	private User user(String firstName) {
		return userRepository.save(User.builder()
				.email("notif-http-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName(firstName)
				.activated(true)
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
