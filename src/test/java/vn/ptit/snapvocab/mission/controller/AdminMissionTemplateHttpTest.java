package vn.ptit.snapvocab.mission.controller;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
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
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** daily_mission.md §8.3 over HTTP → Security → controller → LOCAL MySQL, including the catalog-lock race. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminMissionTemplateHttpTest {

	private static final String BASE = "/api/admin/mission-templates";

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
	JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private String admin;
	private String user;

	@BeforeEach
	void setUp() {
		cleanUp();
		admin = bearer(saveUser(), "ROLE_USER ROLE_ADMIN");
		user = bearer(saveUser(), "ROLE_USER");
	}

	@AfterEach
	void cleanUp() {
		for (String table : List.of("user_daily_mission_claims", "user_daily_mission_progress_logs",
				"user_daily_missions", "mission_templates", "shop_items")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'dm-admin-it-%'");
	}

	@Test
	void requiresAdmin() throws Exception {
		call(null, "GET", BASE, null, 401);
		call(user, "GET", BASE, null, 403);
		call(user, "POST", BASE, body("X"), 403);
	}

	/** A cross-origin 401 must keep CORS headers, otherwise the browser hides it and the client cannot refresh. */
	@Test
	void unauthorizedKeepsCorsHeaders() throws Exception {
		HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE))
				.header("Origin", "http://localhost:3000").header("Authorization", "Bearer invalid").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).isEqualTo(401);
		assertThat(resp.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
	}

	@Test
	void createIsInactiveThenEditAndClearNullable() throws Exception {
		JsonNode created = call(admin, "POST", BASE, quiz("QUIZ_X"), 201).path("data");
		assertThat(created.path("isActive").asBoolean()).isFalse();
		assertThat(created.path("weight").asInt()).isEqualTo(1);
		assertThat(created.path("eligibilityRule").asText()).isEqualTo("NONE");
		long id = created.path("id").asLong();

		Map<String, Object> patch = new HashMap<>();
		patch.put("version", created.path("version").asLong());
		patch.put("name", "Renamed");
		patch.put("minQuizAccuracyPercent", null);
		JsonNode edited = call(admin, "PATCH", BASE + "/" + id, patch, 200).path("data");
		assertThat(edited.path("name").asText()).isEqualTo("Renamed");
		assertThat(edited.path("minQuizAccuracyPercent").isNull()).isTrue();
		assertThat(edited.path("quizRequestedCount").asInt()).isEqualTo(5);
		assertThat(edited.path("version").asLong()).isGreaterThan(created.path("version").asLong());

		assertThat(call(admin, "GET", BASE + "/" + id, null, 200).path("data").path("name").asText()).isEqualTo("Renamed");
		assertThat(call(admin, "GET", BASE + "?active=false&category=QUIZ", null, 200).path("data")).hasSize(1);
		assertThat(call(admin, "GET", BASE + "?active=true", null, 200).path("data")).isEmpty();
	}

	@Test
	void rejectsInvalidWrites() throws Exception {
		long id = call(admin, "POST", BASE, body("ADD_X"), 201).path("data").path("id").asLong();

		assertThat(error(call(admin, "POST", BASE, body("ADD_X"), 409))).isEqualTo("MISSION_TEMPLATE_CODE_EXISTS");
		Map<String, Object> withActive = body("ADD_Y");
		withActive.put("isActive", true);
		assertThat(error(call(admin, "POST", BASE, withActive, 400))).isEqualTo("VALIDATION_ERROR");
		Map<String, Object> quizFilterOnWords = body("ADD_Z");
		quizFilterOnWords.put("quizRequestedCount", 5);
		JsonNode bad = call(admin, "POST", BASE, quizFilterOnWords, 400);
		assertThat(bad.path("data").path("errors").get(0).path("field").asText()).isEqualTo("quizRequestedCount");
		Map<String, Object> noReward = body("ADD_W");
		noReward.put("rewardCoin", 0);
		noReward.put("rewardXp", 0);
		assertThat(error(call(admin, "POST", BASE, noReward, 400))).isEqualTo("VALIDATION_ERROR");
		Map<String, Object> missingItem = body("ADD_V");
		missingItem.put("rewardItemCode", "NO_SUCH_ITEM");
		missingItem.put("rewardItemFallbackCoin", 5);
		assertThat(error(call(admin, "POST", BASE, missingItem, 400))).isEqualTo("REWARD_ITEM_NOT_ELIGIBLE");
		Map<String, Object> itemWithoutFallback = body("ADD_U");
		itemWithoutFallback.put("rewardItemCode", "NO_SUCH_ITEM");
		assertThat(error(call(admin, "POST", BASE, itemWithoutFallback, 400))).isEqualTo("VALIDATION_ERROR");

		assertThat(error(call(admin, "PATCH", BASE + "/" + id, Map.of("version", 0, "code", "NEW"), 409)))
				.isEqualTo("FIELD_LOCKED");
		assertThat(error(call(admin, "PATCH", BASE + "/" + id, Map.of("version", 99, "name", "x"), 409)))
				.isEqualTo("VERSION_CONFLICT");
		assertThat(error(call(admin, "PATCH", BASE + "/" + id, Map.of("name", "x"), 400))).isEqualTo("VALIDATION_ERROR");
		Map<String, Object> nullName = new HashMap<>();
		nullName.put("version", 0);
		nullName.put("name", null);
		assertThat(error(call(admin, "PATCH", BASE + "/" + id, nullName, 400))).isEqualTo("VALIDATION_ERROR");
		assertThat(error(call(admin, "PATCH", BASE + "/999999", Map.of("version", 0), 404)))
				.isEqualTo("MISSION_TEMPLATE_NOT_FOUND");
		assertThat(error(call(admin, "GET", BASE + "/999999", null, 404))).isEqualTo("MISSION_TEMPLATE_NOT_FOUND");
	}

	@Test
	void switchingOffBelowInvariantIs409() throws Exception {
		List<MissionTemplate> core = liveCatalog(4, 1);
		MissionTemplate victim = core.get(0);
		JsonNode resp = call(admin, "PATCH", BASE + "/" + victim.getId(),
				Map.of("version", victim.getVersion(), "isActive", false), 409);
		assertThat(error(resp)).isEqualTo("MISSION_POOL_INSUFFICIENT");
		assertThat(templateRepository.findById(victim.getId()).orElseThrow().getActive()).isTrue();

		// Changing eligibility of an active core template also moves the invariant.
		resp = call(admin, "PATCH", BASE + "/" + victim.getId(),
				Map.of("version", victim.getVersion(), "eligibilityRule", "HAS_IDLE_XP_BOOSTER"), 409);
		assertThat(error(resp)).isEqualTo("MISSION_POOL_INSUFFICIENT");
	}

	/** C=5, S=1: each switch-off alone leaves C=4,S=1 (valid); both together C=3 (invalid). Exactly one wins. */
	@Test
	void concurrentSwitchOffOfTwoCoreTemplatesCannotBothCommit() throws Exception {
		List<MissionTemplate> core = liveCatalog(5, 1);
		CyclicBarrier start = new CyclicBarrier(2);
		List<CompletableFuture<HttpResponse<String>>> calls = core.subList(0, 2).stream()
				.map(t -> CompletableFuture.supplyAsync(() -> {
					try {
						start.await();
						return http.send(request(admin, "PATCH", BASE + "/" + t.getId(),
								Map.of("version", t.getVersion(), "isActive", false)), HttpResponse.BodyHandlers.ofString());
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				}))
				.toList();
		List<HttpResponse<String>> responses = calls.stream().map(CompletableFuture::join).toList();

		assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
		HttpResponse<String> loser = responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
		assertThat(error(json.readTree(loser.body()))).isEqualTo("MISSION_POOL_INSUFFICIENT");
		assertThat(MissionTracker.satisfiesInvariant(templateRepository.findByActiveTrueOrderById())).isTrue();
	}

	// ---- helpers ----

	private List<MissionTemplate> liveCatalog(int core, int slotFive) {
		List<MissionTemplate> coreTemplates = new java.util.ArrayList<>();
		for (int i = 0; i < core; i++) {
			coreTemplates.add(templateRepository.save(template("CORE_" + i, MissionCategory.QUIZ,
					MissionTriggerEvent.QUIZ_COMPLETED)));
		}
		for (int i = 0; i < slotFive; i++) {
			templateRepository.save(template("SLOT5_" + i, MissionCategory.RETENTION,
					MissionTriggerEvent.LEARNING_ACTIVITY_RECORDED));
		}
		return coreTemplates;
	}

	private static MissionTemplate template(String code, MissionCategory category, MissionTriggerEvent trigger) {
		return MissionTemplate.builder().code(code).name(code).description(code).category(category)
				.triggerEvent(trigger).targetValue(1).rewardCoin(10).rewardXp(10)
				.eligibilityRule(MissionEligibilityRule.NONE).active(true).build();
	}

	private static Map<String, Object> body(String code) {
		Map<String, Object> b = new HashMap<>();
		b.put("code", code);
		b.put("name", "Thêm từ");
		b.put("description", "Thêm 5 từ mới có nghĩa");
		b.put("category", "VOCABULARY");
		b.put("triggerEvent", "TOPIC_ITEM_CREATED");
		b.put("targetValue", 5);
		b.put("rewardCoin", 15);
		b.put("rewardXp", 25);
		return b;
	}

	private static Map<String, Object> quiz(String code) {
		Map<String, Object> b = body(code);
		b.put("category", "QUIZ");
		b.put("triggerEvent", "QUIZ_COMPLETED");
		b.put("minQuizAccuracyPercent", 100);
		b.put("quizRequestedCount", 5);
		b.put("targetValue", 1);
		return b;
	}

	private static String error(JsonNode resp) {
		return resp.path("error").asText();
	}

	private HttpRequest request(String token, String method, String path, Object body) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.header("Content-Type", "application/json");
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		return req.build();
	}

	private JsonNode call(String token, String method, String path, Object body, int expected) throws Exception {
		HttpResponse<String> resp = http.send(request(token, method, path, body), HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).withFailMessage("%s %s → %s %s", method, path, resp.statusCode(), resp.body())
				.isEqualTo(expected);
		return resp.body().isEmpty() ? json.nullNode() : json.readTree(resp.body());
	}

	private User saveUser() {
		return userRepository.save(User.builder()
				.email("dm-admin-it-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Tester")
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
