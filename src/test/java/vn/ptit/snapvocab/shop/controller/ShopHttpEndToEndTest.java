package vn.ptit.snapvocab.shop.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.domain.enumeration.ThemeKey;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;

/** HTTP → Security (Bearer JWT) → ShopController → services → LOCAL MySQL, including envelope and error codes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class ShopHttpEndToEndTest {

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
	ShopItemRepository shopItemRepository;
	@Autowired
	JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();
	private User user;
	private String token;

	@BeforeEach
	void setUp() {
		user = userRepository.save(User.builder()
				.email("shop-it-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Learner")
				.activated(true)
				.coin(1000L)
				.build());
		token = bearer(user);
	}

	@AfterEach
	void cleanUp() {
		for (String table : List.of("experience_logs", "booster_activations", "coin_transactions", "user_items",
				"shop_items")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'shop-it-%'");
	}

	@Test
	void requiresAuthentication() {
		ResponseEntity<String> resp = rest.getForEntity("/api/shop/items", String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void fullLearnerFlow() throws Exception {
		ShopItem theme = item(ShopItemType.THEME, 300, b -> b.themeKey(ThemeKey.MIDNIGHT_NEON));
		ShopItem booster = item(ShopItemType.XP_BOOSTER, 100, b -> b.boostDurationMinutes(30).maxQuantity(5));

		JsonNode catalog = call(HttpMethod.GET, "/api/shop/items", null, null, HttpStatus.OK);
		assertThat(catalog.path("data")).hasSize(2);
		assertThat(catalog.path("data").get(0).path("config").path("themeKey").asText()).isEqualTo("MIDNIGHT_NEON");

		JsonNode missingKey = call(HttpMethod.POST, "/api/shop/items/" + theme.getId() + "/purchase",
				Map.of("expectedPrice", 300), null, HttpStatus.BAD_REQUEST);
		assertThat(missingKey.path("error").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

		JsonNode stale = call(HttpMethod.POST, "/api/shop/items/" + theme.getId() + "/purchase",
				Map.of("expectedPrice", 250), "k0", HttpStatus.CONFLICT);
		assertThat(stale.path("error").asText()).isEqualTo("PRICE_CHANGED");
		assertThat(stale.path("data").path("currentPrice").asLong()).isEqualTo(300);

		JsonNode bought = call(HttpMethod.POST, "/api/shop/items/" + theme.getId() + "/purchase",
				Map.of("expectedPrice", 300), "k1", HttpStatus.OK);
		assertThat(bought.path("data").path("coinBalance").asLong()).isEqualTo(700);
		assertThat(bought.path("data").path("replayed").asBoolean()).isFalse();
		JsonNode replay = call(HttpMethod.POST, "/api/shop/items/" + theme.getId() + "/purchase",
				Map.of("expectedPrice", 300), "k1", HttpStatus.OK);
		assertThat(replay.path("data").path("replayed").asBoolean()).isTrue();
		assertThat(replay.path("data").path("coinBalance").asLong()).isEqualTo(700);

		JsonNode equipped = call(HttpMethod.PUT, "/api/me/equipment/THEME", Map.of("itemId", theme.getId()), null,
				HttpStatus.OK);
		assertThat(equipped.path("data").path("item").path("equipped").asBoolean()).isTrue();
		JsonNode badSlot = call(HttpMethod.PUT, "/api/me/equipment/HAT", Map.of("itemId", theme.getId()), null,
				HttpStatus.BAD_REQUEST);
		assertThat(badSlot.path("error").asText()).isEqualTo("INVALID_EQUIPMENT_SLOT");

		call(HttpMethod.POST, "/api/shop/items/" + booster.getId() + "/purchase", Map.of("expectedPrice", 100), "k2",
				HttpStatus.OK);
		JsonNode noKey = call(HttpMethod.POST, "/api/me/items/" + booster.getId() + "/activate", null, null,
				HttpStatus.BAD_REQUEST);
		assertThat(noKey.path("error").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
		JsonNode activated = call(HttpMethod.POST, "/api/me/items/" + booster.getId() + "/activate", null, "a1",
				HttpStatus.OK);
		assertThat(activated.path("data").path("quantity").asInt()).isZero();
		assertThat(activated.path("data").path("activation").path("expiresAt").asText()).isNotBlank();

		JsonNode inventory = call(HttpMethod.GET, "/api/me/items", null, null, HttpStatus.OK);
		assertThat(inventory.path("data").path("activeBooster").path("multiplier").asInt()).isEqualTo(2);
		assertThat(inventory.path("data").path("serverNow").asText()).isNotBlank();
		assertThat(inventory.path("data").path("items")).hasSize(2);

		JsonNode wallet = call(HttpMethod.GET, "/api/me/wallet?size=10", null, null, HttpStatus.OK);
		assertThat(wallet.path("data").path("balance").asLong()).isEqualTo(600);
		assertThat(wallet.path("data").path("transactions")).hasSize(2);

		call(HttpMethod.DELETE, "/api/me/equipment/THEME", null, null, HttpStatus.OK);
	}

	private JsonNode call(HttpMethod method, String url, Object body, String key, HttpStatus expected) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		if (key != null) {
			headers.set("Idempotency-Key", key);
		}
		ResponseEntity<String> resp = rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
		assertThat(resp.getStatusCode()).withFailMessage("%s %s → %s %s", method, url, resp.getStatusCode(),
				resp.getBody()).isEqualTo(expected);
		return json.readTree(resp.getBody());
	}

	private ShopItem item(ShopItemType type, long price,
			java.util.function.UnaryOperator<ShopItem.ShopItemBuilder> config) {
		return shopItemRepository.save(config.apply(ShopItem.builder()
				.code("IT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
				.type(type)
				.name(type + " item")
				.status(ShopItemStatus.PUBLISHED)
				.purchasable(true)
				.price(price)).build());
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
