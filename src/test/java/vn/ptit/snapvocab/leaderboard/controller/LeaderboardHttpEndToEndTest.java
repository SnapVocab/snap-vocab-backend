package vn.ptit.snapvocab.leaderboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.minio.MinioClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.ptit.snapvocab.leaderboard.controller.LeaderboardTestSupport.InMemoryLeaderboardSnapshotStore;
import vn.ptit.snapvocab.leaderboard.controller.LeaderboardTestSupport.MutableClock;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWindow;
import vn.ptit.snapvocab.leaderboard.repository.LeaderboardRepository;
import vn.ptit.snapvocab.leaderboard.service.LeaderboardSnapshotService;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.service.AvatarUrlService;

/**
 * HTTP → Security → leaderboard controllers → snapshot service → LOCAL MySQL (leaderboard_backend.md §11.3).
 * XP is written in the week of 2025-03-03, which no other test touches; Redis is replaced by an in-memory store.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
@Import(LeaderboardTestSupport.Config.class)
class LeaderboardHttpEndToEndTest {

	private static final String LEARNER = "ROLE_USER";
	private static final String ADMIN = "ROLE_USER ROLE_ADMIN";
	/** Tuesday 04/03/2025 09:00 … 11:00 in Vietnam. */
	private static final Instant TUE_09 = Instant.parse("2025-03-04T02:00:00Z");
	private static final Instant TUE_10 = Instant.parse("2025-03-04T03:00:00Z");
	private static final Instant TUE_11 = Instant.parse("2025-03-04T04:00:00Z");
	private static final Instant MON_20 = Instant.parse("2025-03-03T13:00:00Z");

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;
	@MockitoBean
	AvatarUrlService avatarUrlService;
	@MockitoSpyBean
	LeaderboardRepository leaderboardRepository;

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
	@Autowired
	MutableClock clock;
	@Autowired
	InMemoryLeaderboardSnapshotStore store;
	@Autowired
	LeaderboardSnapshotService snapshotService;
	@Autowired
	ObjectProvider<MeterRegistry> meterRegistry;

	private static final List<String> SOURCES = List.of("FLASHCARD_REVIEW", "QUIZ");
	private final ObjectMapper json = new ObjectMapper();

	@BeforeEach
	void setUp() {
		store.clear();
		clock.set(LeaderboardTestSupport.WEDNESDAY_NOON);
		when(avatarUrlService.resolve(isNull())).thenReturn(null);
		when(avatarUrlService.resolve(anyString())).thenAnswer(i -> "https://signed/" + i.getArgument(0));
	}

	@AfterEach
	void cleanUp() {
		String ours = "SELECT id FROM users WHERE email LIKE 'lb-http-%'";
		jdbc.update("DELETE FROM experience_logs WHERE event_key LIKE 'LB-IT:%'");
		jdbc.update("DELETE FROM user_items WHERE user_id IN (" + ours + ")");
		jdbc.update("DELETE FROM shop_items WHERE code LIKE 'LB_IT_%'");
		jdbc.update("DELETE FROM user_authorities WHERE user_id IN (" + ours + ")");
		jdbc.update("DELETE FROM users WHERE email LIKE 'lb-http-%'");
	}

	/** L-01, L-02 (AC-LB-01, AC-LB-07, AC-LB-13). */
	@Test
	void onlyLearningXpCountsBoosterIncluded() throws Exception {
		assertThat(rest.getForEntity("/api/leaderboards", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		User lan = user("Lan", "Nguyễn");
		User idle = user("Idle", "User");
		xp(lan, "QUIZ", 50, 50, TUE_09);
		xp(lan, "FLASHCARD_REVIEW", 2, 0, TUE_09);
		xp(lan, "MISSION_REWARD", 30, 0, TUE_09);
		xp(lan, "BADGE_REWARD", 60, 0, TUE_09);
		xp(lan, "ADMIN_ADJUSTMENT", 500, 0, TUE_09);

		JsonNode board = get(token(lan), "/api/leaderboards");
		assertThat(board.path("me").path("score").asLong()).isEqualTo(102);
		assertThat(board.path("me").path("rank").asInt()).isEqualTo(1);
		JsonNode none = get(token(idle), "/api/leaderboards").path("me");
		assertThat(none.path("eligible").asBoolean()).isTrue();
		assertThat(none.path("rank").isNull()).isTrue();
		assertThat(none.path("score").asLong()).isZero();
		assertThat(none.path("xpToNextRank").isNull()).isTrue();
	}

	/** L-03 (AC-LB-02). */
	@Test
	void weekBoundaryFollowsVietnamTime() throws Exception {
		User minh = user("Minh", "Trần");
		xp(minh, "QUIZ", 40, 0, Instant.parse("2025-03-02T16:59:59.999999Z"));
		xp(minh, "QUIZ", 10, 0, Instant.parse("2025-03-02T17:00:00Z"));
		assertThat(get(token(minh), "/api/leaderboards").path("me").path("score").asLong()).isEqualTo(10);
		assertThat(get(token(minh), "/api/leaderboards?week=PREVIOUS").path("me").path("score").asLong()).isEqualTo(40);
		assertThat(call(token(minh), "/api/leaderboards?week=LAST").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	/** L-04 (AC-LB-05, OQ-LB-07): admins, inactive and banned accounts never rank. */
	@Test
	void adminsInactiveAndBannedAccountsAreNotRanked() throws Exception {
		User admin = user("Admin", "One");
		jdbc.update("INSERT INTO user_authorities (user_id, authority_name) VALUES (?, 'ROLE_ADMIN')", admin.getId());
		User inactive = userRepository.save(builder("Inactive").activated(false).build());
		User banned = userRepository.save(builder("Banned").bannedAt(Instant.parse("2025-03-01T00:00:00Z")).build());
		User lan = user("Lan", "Nguyễn");
		xp(admin, "QUIZ", 500, 0, TUE_09);
		xp(inactive, "QUIZ", 300, 0, TUE_09);
		xp(banned, "QUIZ", 200, 0, TUE_09);
		xp(lan, "QUIZ", 10, 0, TUE_09);

		JsonNode board = get(token(admin, ADMIN), "/api/leaderboards");
		assertThat(board.path("participants").asInt()).isEqualTo(1);
		assertThat(board.path("entries")).hasSize(1);
		assertThat(board.path("entries").get(0).path("score").asLong()).isEqualTo(10);
		assertThat(board.path("me").path("eligible").asBoolean()).isFalse();
		assertThat(board.path("me").path("rank").isNull()).isTrue();
		JsonNode adminBoard = get(token(admin, ADMIN), "/api/admin/leaderboards");
		assertThat(adminBoard.path("eligibleLearners").asLong()).isEqualTo(eligibleLearners());
		assertThat(adminBoard.path("entries")).extracting(e -> e.path("userId").asLong()).containsExactly(lan.getId());
	}

	/** L-05, L-06, L-08 (AC-LB-03, AC-LB-04, AC-LB-08, AC-LB-09). */
	@Test
	void sharedRanksGapsLimitsAndPrivacy() throws Exception {
		List<User> five = fiveFromTheSpecTable();
		int[] ranks = {1, 2, 2, 2, 5};
		long[] scores = {120, 90, 90, 90, 40};
		Long[] gaps = {null, 31L, 31L, 31L, 51L};
		Integer[] next = {null, 1, 1, 1, 2};
		JsonNode board = null;
		for (int i = 0; i < five.size(); i++) {
			board = get(token(five.get(i)), "/api/leaderboards");
			JsonNode me = board.path("me");
			assertThat(me.path("rank").asInt()).isEqualTo(ranks[i]);
			assertThat(me.path("score").asLong()).isEqualTo(scores[i]);
			assertThat(me.path("xpToNextRank").isNull() ? null : me.path("xpToNextRank").asLong()).isEqualTo(gaps[i]);
			assertThat(me.path("nextRank").isNull() ? null : me.path("nextRank").asInt()).isEqualTo(next[i]);
			assertThat(board.path("entries").get(i).path("isMe").asBoolean()).isTrue();
		}
		assertThat(board.path("entries")).extracting(e -> e.path("displayName").asText())
				.containsExactly("P7 User", "P3 User", "P9 User", "P12 User", "P5 User");
		assertThat(board.path("participants").asInt()).isEqualTo(5);
		assertThat(board.path("weekStart").asText()).isEqualTo("2025-03-03");
		assertThat(board.path("weekEnd").asText()).isEqualTo("2025-03-09");
		assertThat(board.path("timezone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(board.path("endsAt").asText()).isEqualTo("2025-03-09T17:00:00Z");
		assertThat(board.path("isFinal").asBoolean(true)).isFalse();
		assertThat(board.has("final")).isFalse();
		JsonNode row = board.path("entries").get(0);
		assertThat(row.has("email")).isFalse();
		assertThat(row.has("userId")).isFalse();
		assertThat(row.has("isMe")).isTrue();
		assertThat(row.has("reviewXp")).isFalse();
		assertThat(board.path("me").has("email")).isFalse();

		String last = token(five.get(4));
		JsonNode three = get(last, "/api/leaderboards?limit=3");
		assertThat(three.path("entries")).hasSize(3);
		assertThat(three.path("me").path("rank").asInt()).isEqualTo(5);
		assertThat(get(last, "/api/leaderboards?limit=0").path("entries")).hasSize(1);
		assertThat(get(last, "/api/leaderboards?limit=500").path("entries")).hasSize(5);
		assertThat(call(last, "/api/leaderboards?limit=abc").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	/** L-07 (AC-LB-06): one response, one snapshot; new XP shows after the next refresh. */
	@Test
	void newXpWaitsForTheNextRefresh() throws Exception {
		List<User> five = fiveFromTheSpecTable();
		User last = five.get(4);
		JsonNode first = get(token(last), "/api/leaderboards");
		xp(last, "QUIZ", 100, 0, Instant.parse("2025-03-05T04:59:00Z"));
		JsonNode second = get(token(last), "/api/leaderboards");
		assertThat(second.path("asOf").asText()).isEqualTo(first.path("asOf").asText());
		assertThat(second.path("me")).isEqualTo(first.path("me"));

		clock.set(LeaderboardTestSupport.WEDNESDAY_NOON.plus(31, ChronoUnit.MINUTES));
		snapshotService.refresh();
		JsonNode third = get(token(last), "/api/leaderboards");
		assertThat(Instant.parse(third.path("asOf").asText())).isAfter(Instant.parse(first.path("asOf").asText()));
		assertThat(third.path("me").path("score").asLong()).isEqualTo(140);
		assertThat(third.path("me").path("rank").asInt()).isEqualTo(1);
	}

	/** L-09, A-03 (AC-LB-10, AC-LB-12): trend against 00:00 today; admin sees the learners' snapshot. */
	@Test
	void trendAgainstMidnightAndAdminSeesTheSameSnapshot() throws Exception {
		User a = user("A", "User");
		User b = user("B", "User");
		User c = user("C", "User");
		User d = user("D", "User");
		xp(a, "QUIZ", 50, 0, TUE_09);
		xp(b, "QUIZ", 40, 0, TUE_09);
		xp(c, "QUIZ", 30, 0, TUE_09);
		xp(c, "QUIZ", 40, 0, Instant.parse("2025-03-05T02:00:00Z"));
		xp(d, "QUIZ", 10, 0, Instant.parse("2025-03-05T02:00:00Z"));

		JsonNode learner = get(token(c), "/api/leaderboards");
		assertThat(learner.path("entries")).extracting(e -> e.path("displayName").asText(),
				e -> e.path("rankChange").isNull() ? null : e.path("rankChange").asInt())
				.containsExactly(tuple("C User", 2), tuple("A User", -1), tuple("B User", -1), tuple("D User", null));
		assertThat(learner.path("me").path("rankChange").asInt()).isEqualTo(2);

		JsonNode admin = get(token(user("Ops", "Admin"), ADMIN), "/api/admin/leaderboards");
		assertThat(admin.path("asOf").asText()).isEqualTo(learner.path("asOf").asText());
		for (int i = 0; i < 4; i++) {
			JsonNode l = learner.path("entries").get(i);
			JsonNode r = admin.path("entries").get(i);
			assertThat(r.path("rank")).isEqualTo(l.path("rank"));
			assertThat(r.path("score")).isEqualTo(l.path("score"));
			assertThat(r.path("rankChange")).isEqualTo(l.path("rankChange"));
		}
		JsonNode previous = get(token(user("Ops", "Two"), ADMIN), "/api/admin/leaderboards?week=PREVIOUS");
		previous.path("entries").forEach(e -> assertThat(e.path("rankChange").isNull()).isTrue());

		clock.set(Instant.parse("2025-03-03T03:00:00Z")); // Monday: no baseline yet
		store.clear();
		get(token(c), "/api/leaderboards").path("entries")
				.forEach(e -> assertThat(e.path("rankChange").isNull()).isTrue());
	}

	/** L-10 (AC-LB-11): last week is final after the grace period and stays put. */
	@Test
	void previousWeekIsFinalAfterTheGracePeriod() throws Exception {
		User lan = user("Lan", "Nguyễn");
		xp(lan, "QUIZ", 70, 0, Instant.parse("2025-02-25T03:00:00Z"));
		JsonNode before = get(token(lan), "/api/leaderboards?week=PREVIOUS");
		assertThat(before.path("isFinal").asBoolean()).isTrue();
		assertThat(before.path("weekStart").asText()).isEqualTo("2025-02-24");
		xp(lan, "QUIZ", 30, 0, TUE_09);
		JsonNode after = get(token(lan), "/api/leaderboards?week=PREVIOUS");
		assertThat(after.path("entries")).isEqualTo(before.path("entries"));
		assertThat(after.path("asOf")).isEqualTo(before.path("asOf"));

		store.clear();
		clock.set(Instant.parse("2025-03-02T17:01:00Z"));
		assertThat(get(token(lan), "/api/leaderboards?week=PREVIOUS").path("isFinal").asBoolean()).isFalse();
		clock.set(Instant.parse("2025-03-02T17:02:00Z"));
		JsonNode settled = get(token(lan), "/api/leaderboards?week=PREVIOUS");
		assertThat(settled.path("isFinal").asBoolean()).isTrue();
		assertThat(Instant.parse(settled.path("asOf").asText())).isAfterOrEqualTo(Instant.parse("2025-03-02T17:02:00Z"));
	}

	/** L-11 (AC-LB-14): a cold start aggregates the live window once however many requests arrive. */
	@Test
	void coldStartComputesTheLiveWindowOnce() throws Exception {
		List<User> five = fiveFromTheSpecTable();
		clearInvocations(leaderboardRepository);
		ExecutorService pool = Executors.newFixedThreadPool(5);
		try {
			List<Callable<JsonNode>> calls = new ArrayList<>();
			for (User u : five) {
				String bearer = token(u);
				calls.add(() -> get(bearer, "/api/leaderboards"));
			}
			List<String> asOf = new ArrayList<>();
			for (Future<JsonNode> f : pool.invokeAll(calls)) {
				asOf.add(f.get().path("asOf").asText());
			}
			assertThat(asOf).hasSize(5).containsOnly(asOf.get(0));
		} finally {
			pool.shutdown();
		}
		verify(leaderboardRepository, times(1)).weeklyScores(eq(Instant.parse("2025-03-02T17:00:00Z")),
				eq(Instant.parse("2025-03-09T17:00:00Z")), any());
	}

	/** L-12: signed avatar and frame, LB-11 names. */
	@Test
	void avatarFrameAndNames() throws Exception {
		User lan = userRepository.save(builder("Lan").lastName("Nguyễn").avatarUrl("avatars/lan.png").build());
		User blank = userRepository.save(builder(" ").build());
		ShopItem frame = shopItemRepository.save(ShopItem.builder()
				.code("LB_IT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
				.type(ShopItemType.AVATAR_FRAME).name("Frame").status(ShopItemStatus.PUBLISHED).purchasable(true)
				.price(100L).frameAssetKey("public/shop/lb-frame.png").build());
		jdbc.update("INSERT INTO user_items (user_id, shop_item_id, item_type, quantity, equipped, acquired_at, updated_at) "
				+ "VALUES (?, ?, 'AVATAR_FRAME', 1, TRUE, NOW(6), NOW(6))", lan.getId(), frame.getId());
		xp(lan, "QUIZ", 50, 0, TUE_09);
		xp(blank, "QUIZ", 40, 0, TUE_09);

		JsonNode entries = get(token(lan), "/api/leaderboards").path("entries");
		assertThat(entries.get(0).path("displayName").asText()).isEqualTo("Lan Nguyễn");
		assertThat(entries.get(0).path("avatarUrl").asText()).isEqualTo("https://signed/avatars/lan.png");
		assertThat(entries.get(0).path("avatarFrameUrl").asText()).isEqualTo("https://signed/public/shop/lb-frame.png");
		assertThat(entries.get(1).path("displayName").asText()).isEqualTo("Người học");
		assertThat(entries.get(1).path("avatarUrl").isNull()).isTrue();
		assertThat(entries.get(1).path("avatarFrameUrl").isNull()).isTrue();
	}

	/** A-01, A-02 (AC-LB-12, AC-LB-13). */
	@Test
	void adminBoardCarriesRawSignals() throws Exception {
		User learner = user("Plain", "Learner");
		assertThat(rest.getForEntity("/api/admin/leaderboards", String.class).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(call(token(learner), "/api/admin/leaderboards").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		fiveFromTheSpecTable();
		User e = user("E", "Burst");
		xp(e, "QUIZ", 50, 0, Instant.parse("2025-03-03T16:59:00Z"));
		xp(e, "FLASHCARD_REVIEW", 2, 0, Instant.parse("2025-03-03T17:00:00Z"));
		for (String at : List.of("2025-03-04T02:00:00Z", "2025-03-04T02:10:00Z", "2025-03-04T02:20:00Z")) {
			xp(e, "QUIZ", 150, 50, Instant.parse(at));
		}

		JsonNode board = get(token(user("Ops", "Admin"), ADMIN), "/api/admin/leaderboards");
		JsonNode entries = board.path("entries");
		assertThat(entries).hasSize(6);
		assertThat(board.path("countedSources")).extracting(JsonNode::asText).containsExactly("FLASHCARD_REVIEW", "QUIZ");
		assertThat(board.path("eligibleLearners").asLong()).isEqualTo(eligibleLearners());
		long total = 0;
		for (JsonNode row : entries) {
			assertThat(row.path("reviewXp").asLong() + row.path("quizXp").asLong()).isEqualTo(row.path("score").asLong());
			assertThat(row.path("email").asText()).startsWith("lb-http-");
			total += row.path("score").asLong();
		}
		assertThat(board.path("totalXp").asLong()).isEqualTo(total);
		assertThat(board.path("participants").asInt()).isEqualTo(6);

		JsonNode burst = entries.get(0);
		assertThat(burst.path("userId").asLong()).isEqualTo(e.getId());
		assertThat(burst.path("score").asLong()).isEqualTo(652);
		assertThat(burst.path("quizXp").asLong()).isEqualTo(650);
		assertThat(burst.path("reviewXp").asLong()).isEqualTo(2);
		assertThat(burst.path("bonusXp").asLong()).isEqualTo(150);
		assertThat(burst.path("events").asInt()).isEqualTo(5);
		assertThat(burst.path("activeDays").asInt()).isEqualTo(2);
		assertThat(burst.path("peakHourXp").asLong()).isEqualTo(600);
		assertThat(burst.path("reachedAt").asText()).isEqualTo("2025-03-04T02:20:00Z");
		assertThat(entries).filteredOn(r -> r.path("score").asLong() == 90)
				.allMatch(r -> r.path("tied").asBoolean() && r.path("rank").asInt() == 3);
	}

	/**
	 * Regression for Q2's hour buckets (whole hours since the window start): every window gives exactly what the
	 * original DATE_FORMAT bucketing gives, at hour, Vietnam-midnight and week boundaries.
	 */
	@Test
	void peakHourMatchesClockHoursAtEveryBoundary() throws Exception {
		User f = user("F", "Edges");
		xp(f, "QUIZ", 70, 0, Instant.parse("2025-03-02T16:00:00Z")); // last hour of last week
		xp(f, "QUIZ", 70, 0, Instant.parse("2025-03-02T16:59:59.999999Z"));
		xp(f, "QUIZ", 10, 0, Instant.parse("2025-03-02T17:00:00Z")); // first instant of this week
		xp(f, "QUIZ", 10, 0, Instant.parse("2025-03-02T17:59:59.999999Z"));
		xp(f, "QUIZ", 100, 0, Instant.parse("2025-03-04T02:59:59.999999Z"));
		xp(f, "QUIZ", 100, 0, Instant.parse("2025-03-04T03:00:00Z"));
		xp(f, "FLASHCARD_REVIEW", 2, 48, Instant.parse("2025-03-04T03:59:59.999999Z"));
		xp(f, "MISSION_REWARD", 500, 0, Instant.parse("2025-03-04T03:30:00Z")); // never counted
		xp(f, "QUIZ", 120, 0, Instant.parse("2025-03-04T16:59:59.999999Z")); // Tue 23:59:59 VN, before the baseline cutoff
		xp(f, "QUIZ", 120, 0, Instant.parse("2025-03-04T17:00:00Z")); // Wed 00:00 VN, after it
		User g = user("G", "Edges");
		xp(g, "QUIZ", 90, 0, Instant.parse("2025-03-04T03:10:00Z"));

		Instant now = LeaderboardTestSupport.WEDNESDAY_NOON;
		for (LeaderboardWindow window : List.of(LeaderboardWindow.live(now),
				LeaderboardWindow.baseline(now).orElseThrow(), LeaderboardWindow.previousWeek(now))) {
			Map<Long, Long> current = leaderboardRepository.peakHourXp(window.start(), window.end(), SOURCES).stream()
					.collect(Collectors.toMap(LeaderboardRepository.PeakRow::getUserId,
							LeaderboardRepository.PeakRow::getPeakHourXp));
			assertThat(current).as(window.key()).isEqualTo(dateFormatPeaks(window)).isNotEmpty();
		}

		String admin = token(user("Ops", "Admin"), ADMIN);
		JsonNode thisWeek = get(admin, "/api/admin/leaderboards").path("entries");
		assertThat(peakOf(thisWeek, f)).isEqualTo(150); // 03:00 + 03:59:59.999999; not 02:59:59.999999
		assertThat(peakOf(thisWeek, g)).isEqualTo(90);
		assertThat(peakOf(get(admin, "/api/admin/leaderboards?week=PREVIOUS").path("entries"), f)).isEqualTo(140);
	}

	/** Job failure: counted and logged; learners keep the last good snapshot until a refresh succeeds. */
	@Test
	void failedRefreshKeepsTheLastSnapshot() throws Exception {
		User lan = user("Lan", "Nguyễn");
		xp(lan, "QUIZ", 50, 0, TUE_09);
		JsonNode first = get(token(lan), "/api/leaderboards");
		double failedBefore = refreshFailures();

		xp(lan, "QUIZ", 30, 0, Instant.parse("2025-03-05T04:59:00Z"));
		clock.set(LeaderboardTestSupport.WEDNESDAY_NOON.plus(31, ChronoUnit.MINUTES));
		doThrow(new IllegalStateException("database down")).when(leaderboardRepository)
				.weeklyScores(any(), any(), any());
		snapshotService.refresh(); // swallows, never kills the scheduler thread
		assertThat(refreshFailures()).isEqualTo(failedBefore + 1);
		JsonNode during = get(token(lan), "/api/leaderboards");
		assertThat(during.path("asOf")).isEqualTo(first.path("asOf"));
		assertThat(during.path("me").path("score").asLong()).isEqualTo(50);

		reset(leaderboardRepository);
		snapshotService.refresh();
		JsonNode after = get(token(lan), "/api/leaderboards");
		assertThat(Instant.parse(after.path("asOf").asText())).isAfter(Instant.parse(first.path("asOf").asText()));
		assertThat(after.path("me").path("score").asLong()).isEqualTo(80);
		assertThat(refreshFailures()).isEqualTo(failedBefore + 1);
	}

	/** The job and learner requests racing on an empty store still aggregate the live window once. */
	@Test
	void concurrentRefreshAndRequestsComputeTheLiveWindowOnce() throws Exception {
		List<User> five = fiveFromTheSpecTable();
		clearInvocations(leaderboardRepository);
		ExecutorService pool = Executors.newFixedThreadPool(5);
		List<String> asOf = new ArrayList<>();
		try {
			List<Callable<String>> calls = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				calls.add(() -> {
					snapshotService.refresh();
					return null;
				});
			}
			for (User u : five.subList(0, 3)) {
				String bearer = token(u);
				calls.add(() -> get(bearer, "/api/leaderboards").path("asOf").asText());
			}
			for (Future<String> f : pool.invokeAll(calls)) {
				if (f.get() != null) {
					asOf.add(f.get());
				}
			}
		} finally {
			pool.shutdown();
		}
		assertThat(asOf).hasSize(3).containsOnly(asOf.get(0));
		verify(leaderboardRepository, times(1)).weeklyScores(eq(Instant.parse("2025-03-02T17:00:00Z")),
				eq(Instant.parse("2025-03-09T17:00:00Z")), any());
	}

	/** Q2 exactly as specified before the hour-bucket change (leaderboard_backend.md §5.3 rev 1.2). */
	private Map<Long, Long> dateFormatPeaks(LeaderboardWindow window) {
		Map<Long, Long> peaks = new HashMap<>();
		jdbc.query("""
				SELECT h.user_id AS userId, MAX(h.xp) AS peakHourXp
				FROM (
				    SELECT el.user_id, CAST(SUM(el.amount) AS SIGNED) AS xp
				    FROM experience_logs el
				    WHERE el.created_at >= ? AND el.created_at < ?
				      AND el.source_type IN ('FLASHCARD_REVIEW', 'QUIZ')
				    GROUP BY el.user_id, DATE_FORMAT(el.created_at, '%Y-%m-%d %H')
				) h
				GROUP BY h.user_id
				""", rs -> {
			peaks.put(rs.getLong("userId"), rs.getLong("peakHourXp"));
		}, Timestamp.from(window.start()), Timestamp.from(window.end()));
		return peaks;
	}

	private static long peakOf(JsonNode entries, User u) {
		for (JsonNode row : entries) {
			if (row.path("userId").asLong() == u.getId()) {
				return row.path("peakHourXp").asLong();
			}
		}
		throw new AssertionError("user " + u.getId() + " not on the board");
	}

	private double refreshFailures() {
		return meterRegistry.getIfAvailable(() -> Metrics.globalRegistry).counter("leaderboard.refresh.failed").count();
	}

	/** The §3.3 table: 120, 90, 90, 90, 40; P9 is created before P12 so it has the smaller id. */
	private List<User> fiveFromTheSpecTable() {
		User p7 = user("P7", "User");
		User p3 = user("P3", "User");
		User p9 = user("P9", "User");
		User p12 = user("P12", "User");
		User p5 = user("P5", "User");
		xp(p7, "QUIZ", 120, 0, TUE_10);
		xp(p3, "QUIZ", 90, 0, TUE_09);
		xp(p9, "QUIZ", 90, 0, TUE_11);
		xp(p12, "QUIZ", 90, 0, TUE_11);
		xp(p5, "QUIZ", 40, 0, MON_20);
		return List.of(p7, p3, p9, p12, p5);
	}

	private void xp(User u, String source, int base, int bonus, Instant at) {
		jdbc.update("INSERT INTO experience_logs (id, user_id, amount, base_amount, bonus_amount, source_type, event_key, "
				+ "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), u.getId(), base + bonus,
				base, bonus, source, "LB-IT:" + UUID.randomUUID(), Timestamp.from(at));
	}

	private long eligibleLearners() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE u.activated = TRUE AND u.banned_at IS NULL "
				+ "AND u.id NOT IN (SELECT ua.user_id FROM user_authorities ua WHERE ua.authority_name = 'ROLE_ADMIN')",
				Long.class);
	}

	private static org.assertj.core.groups.Tuple tuple(Object... values) {
		return org.assertj.core.groups.Tuple.tuple(values);
	}

	private ResponseEntity<String> call(String bearer, String url) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private JsonNode get(String bearer, String url) throws Exception {
		ResponseEntity<String> response = call(bearer, url);
		assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
		return json.readTree(response.getBody()).path("data");
	}

	private User user(String firstName, String lastName) {
		return userRepository.save(builder(firstName).lastName(lastName).build());
	}

	private static User.UserBuilder builder(String firstName) {
		return User.builder()
				.email("lb-http-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName(firstName)
				.activated(true);
	}

	private String token(User u) {
		return token(u, LEARNER);
	}

	private String token(User u, String authorities) {
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
