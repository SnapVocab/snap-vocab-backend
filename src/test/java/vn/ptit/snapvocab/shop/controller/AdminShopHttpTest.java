package vn.ptit.snapvocab.shop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
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
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;

/** Admin Shop API over HTTP → Security → AdminShopController → LOCAL MySQL, plus learner-side effects. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminShopHttpTest {

	private static final String ITEMS = "/api/admin/shop/items";
	private static final String ICON = "public/shop/icon-1.png";
	private static final String FRAME = "public/shop/frame-1.webp";

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

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private User learner;
	private String admin;
	private String user;

	@BeforeEach
	void setUp() throws Exception {
		User adminUser = saveUser(0);
		learner = saveUser(1000);
		admin = bearer(adminUser, "ROLE_USER ROLE_ADMIN");
		user = bearer(learner, "ROLE_USER");
		assetSize(10_000);
	}

	@AfterEach
	void cleanUp() {
		for (String table : List.of("experience_logs", "booster_activations", "coin_transactions", "user_items",
				"shop_items")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'shop-admin-it-%'");
	}

	// ---- authorization ----

	@Test
	void unauthenticatedIs401() throws Exception {
		call(null, "GET", ITEMS, null, 401);
		call(null, "GET", "/api/admin/shop/item-types", null, 401);
	}

	@Test
	void nonAdminIs403() throws Exception {
		call(user, "GET", ITEMS, null, 403);
		call(user, "POST", ITEMS, theme("THEME_X", "MIDNIGHT_NEON"), 403);
		call(user, "GET", "/api/admin/shop/item-types", null, 403);
	}

	// ---- list / search ----

	@Test
	void listFiltersAndSearch() throws Exception {
		long t = create(theme("THEME_OCEAN", "MIDNIGHT_NEON"));
		create(frame("FRAME_GOLD_WEEKLY"));
		create(booster("XP_BOOSTER_X2_30M"));
		publish(t);

		JsonNode all = call(admin, "GET", ITEMS, null, 200);
		assertThat(all.path("data").path("data")).hasSize(3);
		assertThat(all.path("data").path("meta").path("total").asLong()).isEqualTo(3);
		// updatedAt DESC: the just-published theme is the most recently updated
		assertThat(all.path("data").path("data").get(0).path("id").asLong()).isEqualTo(t);

		assertThat(codes(call(admin, "GET", ITEMS + "?type=AVATAR_FRAME", null, 200))).containsExactly("FRAME_GOLD_WEEKLY");
		assertThat(codes(call(admin, "GET", ITEMS + "?status=PUBLISHED", null, 200))).containsExactly("THEME_OCEAN");
		assertThat(codes(call(admin, "GET", ITEMS + "?status=DRAFT", null, 200)))
				.containsExactlyInAnyOrder("FRAME_GOLD_WEEKLY", "XP_BOOSTER_X2_30M");
		assertThat(codes(call(admin, "GET", ITEMS + "?q=gold", null, 200))).containsExactly("FRAME_GOLD_WEEKLY");
		assertThat(codes(call(admin, "GET", ITEMS + "?q=Booster%20name", null, 200))).containsExactly("XP_BOOSTER_X2_30M");
		assertThat(call(admin, "GET", ITEMS + "?page=0&size=2", null, 200).path("data").path("data")).hasSize(2);

		assertThat(error(call(admin, "GET", ITEMS + "?type=CHEST", null, 400))).isEqualTo("VALIDATION_ERROR");
		assertThat(error(call(admin, "GET", ITEMS + "?size=1000", null, 400))).isEqualTo("VALIDATION_ERROR");
	}

	// ---- create ----

	@Test
	void createDraftsOfEveryType() throws Exception {
		JsonNode t = call(admin, "POST", ITEMS, theme("THEME_OCEAN", "MIDNIGHT_NEON"), 201).path("data");
		assertThat(t.path("status").asText()).isEqualTo("DRAFT");
		assertThat(t.path("version").asLong()).isZero();
		assertThat(t.path("themeKey").asText()).isEqualTo("MIDNIGHT_NEON");
		assertThat(t.path("behavior").asText()).isEqualTo("EQUIP");
		assertThat(t.path("createdAt").asText()).isNotBlank();

		JsonNode f = call(admin, "POST", ITEMS, frame("FRAME_GOLD"), 201).path("data");
		assertThat(f.path("type").asText()).isEqualTo("AVATAR_FRAME");
		assertThat(f.path("frameAssetKey").asText()).isEqualTo(FRAME);

		JsonNode b = call(admin, "POST", ITEMS, booster("XP_BOOSTER_X2_30M"), 201).path("data");
		assertThat(b.path("boostDurationMinutes").asInt()).isEqualTo(30);
		assertThat(b.path("maxQuantity").asInt()).isEqualTo(5);
		assertThat(b.path("multiplier").asInt()).isEqualTo(2);

		JsonNode detail = call(admin, "GET", ITEMS + "/" + b.path("id").asLong(), null, 200).path("data");
		assertThat(detail.path("code").asText()).isEqualTo("XP_BOOSTER_X2_30M");
		assertThat(error(call(admin, "GET", ITEMS + "/999999", null, 404))).isEqualTo("SHOP_ITEM_NOT_FOUND");
	}

	@Test
	void createRejectsServerManagedAndMalformedInput() throws Exception {
		Map<String, Object> body = theme("THEME_OCEAN", "MIDNIGHT_NEON");
		body.put("status", "PUBLISHED");
		JsonNode resp = call(admin, "POST", ITEMS, body, 400);
		assertThat(error(resp)).isEqualTo("VALIDATION_ERROR");
		assertThat(resp.path("data").path("errors").get(0).path("field").asText()).isEqualTo("status");

		Map<String, Object> badCode = theme("theme ocean", "MIDNIGHT_NEON");
		assertThat(firstField(call(admin, "POST", ITEMS, badCode, 400))).isEqualTo("code");
		Map<String, Object> badType = theme("THEME_OCEAN", "MIDNIGHT_NEON");
		badType.put("type", "CHEST");
		assertThat(firstField(call(admin, "POST", ITEMS, badType, 400))).isEqualTo("type");
		Map<String, Object> blankName = theme("THEME_OCEAN", "MIDNIGHT_NEON");
		blankName.put("name", " ");
		assertThat(firstField(call(admin, "POST", ITEMS, blankName, 400))).isEqualTo("name");
		Map<String, Object> wrongType = theme("THEME_OCEAN", "MIDNIGHT_NEON");
		wrongType.put("price", "cheap");
		assertThat(firstField(call(admin, "POST", ITEMS, wrongType, 400))).isEqualTo("price");
	}

	@Test
	void duplicateCode() throws Exception {
		create(booster("XP_BOOSTER_X2_30M"));
		assertThat(error(call(admin, "POST", ITEMS, booster("XP_BOOSTER_X2_30M"), 409))).isEqualTo("SHOP_ITEM_CODE_EXISTS");
	}

	@Test
	void invalidTypeConfig() throws Exception {
		Map<String, Object> themeWithBoosterField = theme("THEME_OCEAN", "MIDNIGHT_NEON");
		themeWithBoosterField.put("maxQuantity", 5);
		JsonNode resp = call(admin, "POST", ITEMS, themeWithBoosterField, 400);
		assertThat(error(resp)).isEqualTo("INVALID_SHOP_ITEM_CONFIG");
		assertThat(firstField(resp)).isEqualTo("maxQuantity");

		Map<String, Object> themeWithoutKey = theme("THEME_OCEAN", "MIDNIGHT_NEON");
		themeWithoutKey.remove("themeKey");
		assertThat(error(call(admin, "POST", ITEMS, themeWithoutKey, 400))).isEqualTo("INVALID_SHOP_ITEM_CONFIG");

		Map<String, Object> frameWithoutAsset = frame("FRAME_GOLD");
		frameWithoutAsset.remove("frameAssetKey");
		assertThat(error(call(admin, "POST", ITEMS, frameWithoutAsset, 400))).isEqualTo("INVALID_SHOP_ITEM_CONFIG");

		Map<String, Object> foreignAsset = frame("FRAME_GOLD");
		foreignAsset.put("frameAssetKey", "avatars/someone.png");
		assertThat(error(call(admin, "POST", ITEMS, foreignAsset, 400))).isEqualTo("INVALID_SHOP_ITEM_CONFIG");

		Map<String, Object> boosterWithTheme = booster("XP_BOOSTER_X2_30M");
		boosterWithTheme.put("themeKey", "MIDNIGHT_NEON");
		assertThat(error(call(admin, "POST", ITEMS, boosterWithTheme, 400))).isEqualTo("INVALID_SHOP_ITEM_CONFIG");
	}

	@Test
	void invalidPrice() throws Exception {
		Map<String, Object> noPrice = booster("XP_BOOSTER_X2_30M");
		noPrice.put("purchasable", true);
		noPrice.remove("price");
		assertThat(error(call(admin, "POST", ITEMS, noPrice, 400))).isEqualTo("INVALID_PRICE");

		Map<String, Object> zero = booster("XP_BOOSTER_X2_30M");
		zero.put("price", 0);
		assertThat(error(call(admin, "POST", ITEMS, zero, 400))).isEqualTo("INVALID_PRICE");

		Map<String, Object> tooHigh = booster("XP_BOOSTER_X2_30M");
		tooHigh.put("price", 100_001);
		assertThat(error(call(admin, "POST", ITEMS, tooHigh, 400))).isEqualTo("INVALID_PRICE");

		Map<String, Object> notForSale = booster("XP_BOOSTER_X2_30M");
		notForSale.put("purchasable", false);
		notForSale.remove("price");
		call(admin, "POST", ITEMS, notForSale, 201); // reward-only item: price optional
	}

	@Test
	void invalidAvailabilityWindow() throws Exception {
		Map<String, Object> body = booster("XP_BOOSTER_X2_30M");
		body.put("availableFrom", "2026-10-02T00:00:00Z");
		body.put("availableUntil", "2026-10-01T00:00:00Z");
		assertThat(error(call(admin, "POST", ITEMS, body, 400))).isEqualTo("INVALID_AVAILABILITY_WINDOW");
		body.put("availableUntil", "2026-10-02T00:00:00Z");
		assertThat(error(call(admin, "POST", ITEMS, body, 400))).isEqualTo("INVALID_AVAILABILITY_WINDOW");
		body.put("availableUntil", "2026-10-03T00:00:00Z");
		call(admin, "POST", ITEMS, body, 201);
	}

	@Test
	void invalidThemeKey() throws Exception {
		assertThat(error(call(admin, "POST", ITEMS, theme("THEME_RAINBOW", "RAINBOW"), 400))).isEqualTo("INVALID_THEME_KEY");
		// DEFAULT is the app's built-in theme, never a ShopItem (shop.md §2.2)
		assertThat(error(call(admin, "POST", ITEMS, theme("THEME_DEFAULT", "DEFAULT"), 400))).isEqualTo("INVALID_THEME_KEY");
		create(theme("THEME_OCEAN", "MIDNIGHT_NEON"));
		// one theme is sold by one item only
		assertThat(error(call(admin, "POST", ITEMS, theme("THEME_OCEAN_2", "MIDNIGHT_NEON"), 400)))
				.isEqualTo("INVALID_SHOP_ITEM_CONFIG");
	}

	@Test
	void boosterConfigBounds() throws Exception {
		for (Object[] bad : new Object[][] { { "boostDurationMinutes", 4 }, { "boostDurationMinutes", 181 },
				{ "maxQuantity", 0 }, { "maxQuantity", 11 } }) {
			Map<String, Object> body = booster("XP_BOOSTER_BAD");
			body.put((String) bad[0], bad[1]);
			JsonNode resp = call(admin, "POST", ITEMS, body, 400);
			assertThat(error(resp)).isEqualTo("INVALID_SHOP_ITEM_CONFIG");
			assertThat(firstField(resp)).isEqualTo(bad[0]);
		}
		Map<String, Object> min = booster("XP_BOOSTER_MIN");
		min.put("boostDurationMinutes", 5);
		min.put("maxQuantity", 1);
		call(admin, "POST", ITEMS, min, 201);
		Map<String, Object> max = booster("XP_BOOSTER_MAX");
		max.put("boostDurationMinutes", 180);
		max.put("maxQuantity", 10);
		call(admin, "POST", ITEMS, max, 201);
	}

	// ---- publish ----

	@Test
	void publishValidItem() throws Exception {
		long id = create(frame("FRAME_GOLD"));
		JsonNode published = call(admin, "POST", ITEMS + "/" + id + "/publish", null, 200).path("data");
		assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
		assertThat(published.path("publishedAt").asText()).isNotBlank();
		assertThat(error(call(admin, "POST", ITEMS + "/" + id + "/publish", null, 409))).isEqualTo("INVALID_STATUS_TRANSITION");
	}

	@Test
	void publishInvalidItem() throws Exception {
		Map<String, Object> noIcon = booster("XP_BOOSTER_X2_30M");
		noIcon.remove("iconKey");
		long id = create(noIcon);
		JsonNode resp = call(admin, "POST", ITEMS + "/" + id + "/publish", null, 400);
		assertThat(error(resp)).isEqualTo("VALIDATION_ERROR");
		assertThat(firstField(resp)).isEqualTo("iconKey");
		assertThat(status(id)).isEqualTo("DRAFT");

		long frameId = create(frame("FRAME_HEAVY"));
		assetSize(400 * 1024); // frame limit is 300 KB, icon 200 KB
		JsonNode heavy = call(admin, "POST", ITEMS + "/" + frameId + "/publish", null, 400);
		assertThat(heavy.path("data").path("errors").findValuesAsText("field")).contains("iconKey", "frameAssetKey");
		assertThat(status(frameId)).isEqualTo("DRAFT");
	}

	// ---- update / locking ----

	@Test
	void publishedLocksIdentityAndRuntimeFields() throws Exception {
		long themeId = create(theme("THEME_OCEAN", "MIDNIGHT_NEON"));
		publish(themeId);
		long v = version(themeId);

		JsonNode code = call(admin, "PATCH", ITEMS + "/" + themeId, Map.of("version", v, "code", "THEME_SEA"), 409);
		assertThat(error(code)).isEqualTo("FIELD_LOCKED");
		assertThat(code.path("data").path("fields").get(0).asText()).isEqualTo("code");
		assertThat(error(call(admin, "PATCH", ITEMS + "/" + themeId, Map.of("version", v, "type", "AVATAR_FRAME"), 409)))
				.isEqualTo("FIELD_LOCKED");
		assertThat(error(call(admin, "PATCH", ITEMS + "/" + themeId, Map.of("version", v, "themeKey", "SUNSET_AMBER"), 409)))
				.isEqualTo("FIELD_LOCKED");
		// a locked field sent unchanged is fine (full-form save)
		call(admin, "PATCH", ITEMS + "/" + themeId, Map.of("version", v, "code", "THEME_OCEAN", "name", "Ocean"), 200);

		long boosterId = create(booster("XP_BOOSTER_X2_30M"));
		publish(boosterId);
		JsonNode duration = call(admin, "PATCH", ITEMS + "/" + boosterId,
				Map.of("version", version(boosterId), "boostDurationMinutes", 60), 409);
		assertThat(error(duration)).isEqualTo("FIELD_LOCKED");
		assertThat(call(admin, "GET", ITEMS + "/" + boosterId, null, 200).path("data").path("boostDurationMinutes").asInt())
				.isEqualTo(30);
	}

	@Test
	void draftAllowsTypeChangeAndPublishedAllowsMutableFields() throws Exception {
		long id = create(theme("THEME_OCEAN", "MIDNIGHT_NEON"));
		Map<String, Object> toBooster = new HashMap<>(Map.of("version", 0, "type", "XP_BOOSTER",
				"boostDurationMinutes", 30, "maxQuantity", 5));
		toBooster.put("themeKey", null);
		JsonNode changed = call(admin, "PATCH", ITEMS + "/" + id, toBooster, 200).path("data");
		assertThat(changed.path("type").asText()).isEqualTo("XP_BOOSTER");
		assertThat(changed.path("themeKey").isNull()).isTrue();

		publish(id);
		long v = version(id);
		JsonNode updated = call(admin, "PATCH", ITEMS + "/" + id,
				Map.of("version", v, "price", 500, "maxQuantity", 3, "name", "Booster 2"), 200).path("data");
		assertThat(updated.path("price").asLong()).isEqualTo(500);
		assertThat(updated.path("maxQuantity").asInt()).isEqualTo(3);
		assertThat(updated.path("version").asLong()).isEqualTo(v + 1);

		assertThat(error(call(admin, "PATCH", ITEMS + "/" + id, Map.of("version", v + 1, "price", 0), 400)))
				.isEqualTo("INVALID_PRICE");
		assertThat(error(call(admin, "PATCH", ITEMS + "/" + id, Map.of("price", 600), 400))).isEqualTo("VALIDATION_ERROR");
		// a published item keeps a valid, uploaded icon
		Map<String, Object> noIcon = new HashMap<>(Map.of("version", v + 1));
		noIcon.put("iconKey", null);
		assertThat(firstField(call(admin, "PATCH", ITEMS + "/" + id, noIcon, 400))).isEqualTo("iconKey");
		assertThat(error(call(admin, "PATCH", ITEMS + "/" + id, Map.of("version", v + 1, "publishedAt", "2026-01-01T00:00:00Z"), 400)))
				.isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void optimisticVersionConflict() throws Exception {
		long id = create(booster("XP_BOOSTER_X2_30M"));
		call(admin, "PATCH", ITEMS + "/" + id, Map.of("version", 0, "name", "First admin"), 200);
		JsonNode stale = call(admin, "PATCH", ITEMS + "/" + id, Map.of("version", 0, "name", "Second admin"), 409);
		assertThat(error(stale)).isEqualTo("VERSION_CONFLICT");
		assertThat(stale.path("data").path("currentVersion").asLong()).isEqualTo(1);
		assertThat(call(admin, "GET", ITEMS + "/" + id, null, 200).path("data").path("name").asText())
				.isEqualTo("First admin");
	}

	// ---- archive / delete ----

	@Test
	void archiveLifecycle() throws Exception {
		long draft = create(booster("XP_BOOSTER_X2_30M"));
		assertThat(error(call(admin, "POST", ITEMS + "/" + draft + "/archive", null, 409))).isEqualTo("INVALID_STATUS_TRANSITION");

		publish(draft);
		JsonNode archived = call(admin, "POST", ITEMS + "/" + draft + "/archive", null, 200).path("data");
		assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
		assertThat(archived.path("purchasable").asBoolean()).isFalse();

		assertThat(error(call(admin, "POST", ITEMS + "/" + draft + "/archive", null, 409))).isEqualTo("INVALID_STATUS_TRANSITION");
		assertThat(error(call(admin, "POST", ITEMS + "/" + draft + "/publish", null, 409))).isEqualTo("INVALID_STATUS_TRANSITION");
		long v = version(draft);
		assertThat(error(call(admin, "PATCH", ITEMS + "/" + draft, Map.of("version", v, "purchasable", true, "price", 100), 409)))
				.isEqualTo("FIELD_LOCKED");
		call(admin, "PATCH", ITEMS + "/" + draft, Map.of("version", v, "name", "Retired booster"), 200);
	}

	@Test
	void learnerCatalogReflectsLifecycleAndOwnershipSurvivesArchive() throws Exception {
		long draft = create(booster("XP_BOOSTER_DRAFT"));
		long frameId = create(frame("FRAME_GOLD"));
		publish(frameId);
		assertThat(catalogCodes()).containsExactly("FRAME_GOLD");
		assertThat(catalogCodes()).doesNotContain("XP_BOOSTER_DRAFT");

		call(user, "POST", "/api/shop/items/" + frameId + "/purchase", Map.of("expectedPrice", 100), 200,
				"Idempotency-Key", "buy-1");
		call(user, "PUT", "/api/me/equipment/AVATAR_FRAME", Map.of("itemId", frameId), 200);

		call(admin, "POST", ITEMS + "/" + frameId + "/archive", null, 200);
		assertThat(catalogCodes()).isEmpty();
		assertThat(error(call(user, "POST", "/api/shop/items/" + frameId + "/purchase", Map.of("expectedPrice", 100), 409,
				"Idempotency-Key", "buy-2"))).isEqualTo("ITEM_NOT_AVAILABLE");

		JsonNode inventory = call(user, "GET", "/api/me/items", null, 200).path("data").path("items");
		assertThat(inventory).hasSize(1);
		assertThat(inventory.get(0).path("status").asText()).isEqualTo("ARCHIVED");
		assertThat(inventory.get(0).path("equipped").asBoolean()).isTrue();
		call(user, "DELETE", "/api/me/equipment/AVATAR_FRAME", null, 200);
		call(user, "PUT", "/api/me/equipment/AVATAR_FRAME", Map.of("itemId", frameId), 200);
		assertThat(draft).isPositive();
	}

	@Test
	void deleteOnlyDrafts() throws Exception {
		long draft = create(booster("XP_BOOSTER_DRAFT"));
		call(admin, "DELETE", ITEMS + "/" + draft, null, 204);
		call(admin, "GET", ITEMS + "/" + draft, null, 404);

		long published = create(booster("XP_BOOSTER_LIVE"));
		publish(published);
		assertThat(error(call(admin, "DELETE", ITEMS + "/" + published, null, 409))).isEqualTo("ITEM_CANNOT_BE_DELETED");

		call(admin, "POST", ITEMS + "/" + published + "/archive", null, 200);
		assertThat(error(call(admin, "DELETE", ITEMS + "/" + published, null, 409))).isEqualTo("ITEM_CANNOT_BE_DELETED");
		assertThat(status(published)).isEqualTo("ARCHIVED");
	}

	// ---- metadata / assets ----

	@Test
	void itemTypesMetadata() throws Exception {
		create(theme("THEME_OCEAN", "MIDNIGHT_NEON"));
		JsonNode data = call(admin, "GET", "/api/admin/shop/item-types", null, 200).path("data");

		assertThat(data.path("types").findValuesAsText("type")).containsExactly("THEME", "AVATAR_FRAME", "XP_BOOSTER");
		JsonNode themeKey = data.path("types").get(0).path("fields").get(0);
		assertThat(themeKey.path("name").asText()).isEqualTo("themeKey");
		assertThat(themeKey.path("inputType").asText()).isEqualTo("SELECT");
		assertThat(themeKey.path("options").toString()).contains("SUNSET_AMBER").doesNotContain("MIDNIGHT_NEON", "DEFAULT");
		assertThat(themeKey.path("editableIn").toString()).isEqualTo("[\"DRAFT\"]");

		JsonNode xp = data.path("types").get(2);
		assertThat(xp.path("behavior").asText()).isEqualTo("ACTIVATE");
		assertThat(xp.path("fixed").path("multiplier").asInt()).isEqualTo(2);
		JsonNode duration = xp.path("fields").get(0);
		assertThat(duration.path("min").asInt()).isEqualTo(5);
		assertThat(duration.path("max").asInt()).isEqualTo(180);
		assertThat(duration.path("defaultValue").asInt()).isEqualTo(30);

		JsonNode price = StreamSupport.stream(data.path("commonFields").spliterator(), false)
				.filter(f -> f.path("name").asText().equals("price")).findFirst().orElseThrow();
		assertThat(price.path("max").asLong()).isEqualTo(100_000);
	}

	@Test
	void assetUploadUrlIsScopedToShopPrefix() throws Exception {
		JsonNode ok = call(admin, "POST", "/api/admin/shop/assets/upload-url", Map.of("fileName", "gold.PNG"), 200);
		assertThat(ok.path("data").path("objectName").asText()).startsWith("public/shop/").endsWith(".png");
		assertThat(ok.path("data").path("contentType").asText()).isEqualTo("image/png");
		assertThat(error(call(admin, "POST", "/api/admin/shop/assets/upload-url", Map.of("fileName", "x.gif"), 400)))
				.isEqualTo("VALIDATION_ERROR");
		call(user, "POST", "/api/admin/shop/assets/upload-url", Map.of("fileName", "gold.png"), 403);
	}

	// ---- helpers ----

	private Map<String, Object> base(String code, String type) {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("type", type);
		body.put("name", type.equals("XP_BOOSTER") ? "Booster name" : code + " name");
		body.put("description", "desc");
		body.put("iconKey", ICON);
		body.put("purchasable", true);
		body.put("price", 100);
		return body;
	}

	private Map<String, Object> theme(String code, String themeKey) {
		Map<String, Object> body = base(code, "THEME");
		body.put("themeKey", themeKey);
		return body;
	}

	private Map<String, Object> frame(String code) {
		Map<String, Object> body = base(code, "AVATAR_FRAME");
		body.put("frameAssetKey", FRAME);
		return body;
	}

	private Map<String, Object> booster(String code) {
		Map<String, Object> body = base(code, "XP_BOOSTER");
		body.put("boostDurationMinutes", 30);
		body.put("maxQuantity", 5);
		return body;
	}

	private long create(Map<String, Object> body) throws Exception {
		return call(admin, "POST", ITEMS, body, 201).path("data").path("id").asLong();
	}

	private void publish(long id) throws Exception {
		call(admin, "POST", ITEMS + "/" + id + "/publish", null, 200);
	}

	private long version(long id) throws Exception {
		return call(admin, "GET", ITEMS + "/" + id, null, 200).path("data").path("version").asLong();
	}

	private String status(long id) throws Exception {
		return call(admin, "GET", ITEMS + "/" + id, null, 200).path("data").path("status").asText();
	}

	private List<String> catalogCodes() throws Exception {
		return call(user, "GET", "/api/shop/items", null, 200).path("data").findValuesAsText("code");
	}

	private static List<String> codes(JsonNode page) {
		return page.path("data").path("data").findValuesAsText("code");
	}

	private static String error(JsonNode resp) {
		return resp.path("error").asText();
	}

	private static String firstField(JsonNode resp) {
		return resp.path("data").path("errors").get(0).path("field").asText();
	}

	private void assetSize(long bytes) throws Exception {
		StatObjectResponse stat = mock(StatObjectResponse.class);
		when(stat.size()).thenReturn(bytes);
		when(minioClient.statObject(any())).thenReturn(stat);
	}

	private JsonNode call(String token, String method, String path, Object body, int expected, String... headers)
			throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.header("Content-Type", "application/json");
		if (token != null) {
			req.header("Authorization", "Bearer " + token);
		}
		if (headers.length > 0) {
			req.headers(headers);
		}
		HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).withFailMessage("%s %s → %s %s", method, path, resp.statusCode(), resp.body())
				.isEqualTo(expected);
		return resp.body().isEmpty() ? json.nullNode() : json.readTree(resp.body());
	}

	private User saveUser(long coin) {
		return userRepository.save(User.builder()
				.email("shop-admin-it-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Tester")
				.activated(true)
				.coin(coin)
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
