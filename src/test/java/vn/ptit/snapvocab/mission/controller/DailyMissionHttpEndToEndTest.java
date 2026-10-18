package vn.ptit.snapvocab.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** HTTP → Security (Bearer JWT) → DailyMissionController → services → LOCAL MySQL: envelope, headers, error codes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class DailyMissionHttpEndToEndTest {

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
	MissionTemplateRepository templateRepository;
	@Autowired
	JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private String token;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.builder()
				.email("dm-http-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Learner")
				.activated(true)
				.build());
		token = bearer(user);
	}

	@AfterEach
	void cleanUp() {
		for (String table : List.of("user_daily_mission_progress_logs", "user_daily_missions", "mission_templates")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'dm-http-%'");
	}

	@Test
	void requiresAuthentication() {
		assertThat(rest.getForEntity("/api/me/daily-missions", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void emptyCatalogIs503WithAStableCode() throws Exception {
		ResponseEntity<String> resp = exchange(HttpMethod.GET, "/api/me/daily-missions", null);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(json.readTree(resp.getBody()).path("error").asText()).isEqualTo("MISSION_POOL_INSUFFICIENT");
	}

	@Test
	void todayAndClaimErrorsOverHttp() throws Exception {
		List<MissionTemplate> catalog = vn.ptit.snapvocab.mission.service.MissionCatalogSeederAccess.mvpCatalog();
		templateRepository.saveAll(catalog);

		ResponseEntity<String> today = exchange(HttpMethod.GET, "/api/me/daily-missions", null);
		assertThat(today.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(today.getHeaders().getCacheControl()).contains("no-store");
		JsonNode data = json.readTree(today.getBody()).path("data");
		assertThat(data.path("timezone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(data.path("missions")).hasSize(5);
		JsonNode first = data.path("missions").get(0);
		assertThat(first.path("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(first.path("reward").has("coin")).isTrue();
		long missionId = first.path("id").asLong();

		ResponseEntity<String> noKey = exchange(HttpMethod.POST, "/api/me/daily-missions/" + missionId + "/claim", null);
		assertThat(noKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(json.readTree(noKey.getBody()).path("error").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

		ResponseEntity<String> notDone = exchange(HttpMethod.POST, "/api/me/daily-missions/" + missionId + "/claim", "k1");
		assertThat(notDone.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(json.readTree(notDone.getBody()).path("error").asText()).isEqualTo("MISSION_NOT_COMPLETED");

		ResponseEntity<String> missing = exchange(HttpMethod.POST, "/api/me/daily-missions/999999999/claim", "k2");
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(json.readTree(missing.getBody()).path("error").asText()).isEqualTo("MISSION_NOT_FOUND");
	}

	private ResponseEntity<String> exchange(HttpMethod method, String url, String key) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		if (key != null) {
			headers.set("Idempotency-Key", key);
		}
		return rest.exchange(url, method, new HttpEntity<>(headers), String.class);
	}

	private String bearer(User u) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
				.subject(u.getEmail())
				.claim(SecurityUtils.AUTHORITIES_CLAIM, "ROLE_USER")
				.claim(SecurityUtils.USER_ID_CLAIM, u.getId())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims))
				.getTokenValue();
	}
}
