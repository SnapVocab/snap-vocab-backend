package vn.ptit.snapvocab.adminstats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
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
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Cards;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Funnel;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.HardWord;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.ItemSales;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.MissionCoin;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Reliability;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Segment;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.TopicStat;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.CoinDay;
import vn.ptit.snapvocab.adminstats.service.AdminAnalyticsService;
import vn.ptit.snapvocab.adminstats.service.StatsWindow;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/**
 * admin_analytics_backend.md §10 on LOCAL MySQL. Scan tables are wiped per test (nothing else reads them); every other
 * number is a before/after delta, so data other tests leave behind does not matter. Same mocks as
 * AdminOverviewHttpTest, so the Spring context is shared. The admin repeats the learners' activity everywhere (A-18).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminAnalyticsHttpTest {

	private static final String PREFIX = "IT_AN_";
	private static final String EMAIL = "analytics-it-";
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
			.withZone(ZoneOffset.UTC);

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
	@Autowired
	AdminAnalyticsService service;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private final StatsWindow w = StatsWindow.now(ProgressService.ZONE);
	private final Instant todayStart = w.start(w.today());
	private User a;
	private User b;
	private User admin;

	@BeforeEach
	void setUp() {
		cleanUp();
		when(cacheManager.getCache(anyString())).thenAnswer(inv -> new ConcurrentMapCache(inv.getArgument(0)));
		a = saveUser(true);
		b = saveUser(true);
		admin = grantAdmin(saveUser(true));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM scan_detections");
		jdbc.update("DELETE FROM scan_requests");
		String users = "SELECT id FROM users WHERE email LIKE '" + EMAIL + "%'";
		String collections = "SELECT id FROM collections WHERE name LIKE 'IT AN %' OR owner_id IN (" + users + ")";
		String topics = "SELECT id FROM topics WHERE collection_id IN (SELECT id FROM (" + collections + ") c)";
		String items = "SELECT id FROM topic_items WHERE topic_id IN (SELECT id FROM (" + topics + ") t)";
		String quizzes = "SELECT id FROM quizzes WHERE user_id IN (" + users + ")";
		for (String sql : List.of(
				"DELETE FROM quiz_questions WHERE quiz_id IN (SELECT id FROM (" + quizzes + ") q)",
				"DELETE FROM quizzes WHERE user_id IN (" + users + ")",
				"DELETE FROM fsrs_records WHERE user_id IN (" + users + ")",
				"DELETE FROM topic_item_attribute_values WHERE group_instance_id IN (SELECT id FROM"
						+ " topic_item_attribute_groups WHERE topic_item_id IN (SELECT id FROM (" + items + ") i))",
				"DELETE FROM topic_item_attribute_groups WHERE topic_item_id IN (SELECT id FROM (" + items + ") i)",
				"DELETE FROM topic_items WHERE topic_id IN (SELECT id FROM (" + topics + ") t)",
				"DELETE FROM topics WHERE parent_id IS NOT NULL AND collection_id IN (SELECT id FROM (" + collections
						+ ") c)",
				"DELETE FROM topics WHERE collection_id IN (SELECT id FROM (" + collections + ") c)",
				"DELETE FROM collections WHERE id IN (SELECT id FROM (" + collections + ") c)",
				"DELETE FROM learning_events WHERE user_id IN (" + users + ")",
				"DELETE FROM coin_transactions WHERE user_id IN (" + users + ")",
				"DELETE FROM user_word_credits WHERE user_id IN (" + users + ")",
				"DELETE FROM user_daily_mission_progress_logs WHERE user_daily_mission_id IN (SELECT id FROM"
						+ " user_daily_missions WHERE user_id IN (" + users + "))",
				"DELETE FROM user_daily_missions WHERE user_id IN (" + users + ")",
				"DELETE FROM user_badge_word_credits WHERE user_id IN (" + users + ")",
				"DELETE FROM experience_logs WHERE user_id IN (" + users + ")",
				"DELETE FROM user_authorities WHERE user_id IN (" + users + ")",
				"DELETE FROM users WHERE email LIKE '" + EMAIL + "%'",
				"DELETE FROM mission_templates WHERE code LIKE '" + PREFIX + "%'",
				"DELETE FROM shop_items WHERE code LIKE '" + PREFIX + "%'")) {
			jdbc.update(sql);
		}
	}

	// ---- A-01, A-18 (HTTP) ----

	@Test
	void everyWindowHasItsDaysAndOnlyAdminsMayRead() throws Exception { // A-01, AC-ANL-01
		String token = bearer(admin, "ROLE_USER ROLE_ADMIN");
		JsonNode week = null;
		for (int days : List.of(7, 30, 90)) {
			JsonNode data = get(token, "?days=" + days, 200).path("data");
			assertThat(data.path("days").asInt()).isEqualTo(days);
			for (JsonNode series : List.of(data.path("learning").path("daily"), data.path("economy").path("daily"),
					data.path("scan").path("daily"))) {
				assertThat(series).hasSize(days);
				assertThat(series.get(days - 1).path("date").asText()).isEqualTo(w.today().toString());
			}
			assertThat(data.path("learning").path("cards").has("new")).isTrue();
			assertThat(data.path("scan").path("reliability").has("HIGH")).isTrue();
			if (days == 7) {
				week = data.path("retention");
			} else {
				assertThat(data.path("retention")).isEqualTo(week);
			}
		}
		assertThat(get(token, "", 200).path("data").path("days").asInt()).isEqualTo(30);
		assertThat(get(token, "?days=14", 400).path("error").asText()).contains("7, 30 or 90");
		get(token, "?days=abc", 400);
		get(null, "?days=7", 401);
		get(bearer(a, "ROLE_USER"), "?days=7", 403);
	}

	// ---- Learning ----

	@Test
	void cardsCountReviewedCardsAndOwnWordsNeverReviewed() { // A-02, A-03, AC-ANL-02
		Cards before = service.analytics(7).learning().cards();
		Instant now = Instant.now();
		for (User u : List.of(a, admin)) {
			long own = topic(u, "USER");
			card(u, item(own), 2, 5, now.plus(5, ChronoUnit.MINUTES), 0); // review, due later
			card(u, item(own), 2, 30, now.plus(1, ChronoUnit.DAYS), 0); // mastered
			card(u, item(own), 1, 1, now.minus(5, ChronoUnit.MINUTES), 0); // learning, due
			card(u, item(own), 4, 1, now.minus(1, ChronoUnit.DAYS), 0); // suspended, never due
			item(own); // saved, never reviewed
			card(u, item(topic(null, "SYSTEM")), 2, 3, now.plus(1, ChronoUnit.DAYS), 0); // a system card
			item(topic(null, "SYSTEM")); // a system word nobody reviewed: nobody's card
		}

		Cards after = service.analytics(7).learning().cards();
		assertThat(new Cards(after.newCount() - before.newCount(), after.learning() - before.learning(),
				after.relearning() - before.relearning(), after.review() - before.review(),
				after.mastered() - before.mastered(), after.suspended() - before.suspended(),
				after.dueToday() - before.dueToday())).isEqualTo(new Cards(1, 1, 0, 2, 1, 1, 1));
	}

	@Test
	void topicsAndHardWordsReadCompletedSystemQuizzes() throws Exception { // A-04, A-05, A-06, AC-ANL-03
		String token = bearer(admin, "ROLE_USER ROLE_ADMIN");
		long collection = call(token, "POST", "/api/collections",
				Map.of("name", "IT AN " + UUID.randomUUID(), "type", "SYSTEM"), 200).path("data").path("id").asLong();
		long noun = call(token, "POST", "/api/collections/" + collection + "/topics", Map.of("name", "Noun"), 200)
				.path("data").path("id").asLong();
		long animals = call(token, "POST", "/api/collections/" + collection + "/topics",
				Map.of("name", "Animals", "parentId", noun), 200).path("data").path("id").asLong();
		long hard = call(token, "POST", "/api/collections/" + collection + "/topics",
				Map.of("name", "Hard", "parentId", noun), 200).path("data").path("id").asLong();
		long cat = word(token, animals, "it-an-cat", "mèo");
		List<Long> hardItems = new ArrayList<>();
		for (int i = 1; i <= 25; i++) {
			hardItems.add(word(token, hard, "it-an-hard-" + i, "khó " + i));
		}
		long noWord = word(token, hard, "it-an-untranslated", null); // no quiz target word: skipped

		// A-04: A 5 correct + 2 wrong + 2 open, B 3 correct + 2 wrong; a cancelled quiz and the admin do not count.
		quiz(a, animals, "COMPLETED", outcomes(cat, 5, 2, 2));
		quiz(b, animals, "COMPLETED", outcomes(cat, 3, 2, 0));
		quiz(a, animals, "CANCELLED", outcomes(cat, 4, 4, 0));
		quiz(admin, animals, "COMPLETED", outcomes(cat, 0, 9, 0));
		// A-06: item i is answered wrong 50 − i times; the untranslated item is wrong most of all.
		List<Object[]> hardAnswers = new ArrayList<>();
		for (int i = 0; i < hardItems.size(); i++) {
			hardAnswers.addAll(outcomes(hardItems.get(i), 1, 49 - i, 0));
		}
		hardAnswers.addAll(outcomes(noWord, 0, 100, 0));
		quiz(a, hard, "COMPLETED", hardAnswers);
		card(a, hardItems.get(0), 2, 5, Instant.now(), 2);
		card(b, hardItems.get(0), 2, 5, Instant.now(), 3);
		card(admin, hardItems.get(0), 2, 5, Instant.now(), 7);
		// A-05: the learner's own collection is not system content.
		long own = topic(a, "USER");
		long ownItem = item(own);
		quiz(a, own, "COMPLETED", outcomes(ownItem, 0, 60, 0));

		AdminAnalyticsDTO.Learning l = service.analytics(7).learning();
		assertThat(l.topics()).filteredOn(t -> t.topicId() == animals)
				.containsExactly(new TopicStat(collection, noun, "Noun", animals, "Animals", 12, 8, 2));
		assertThat(l.topics()).extracting(TopicStat::topicId).doesNotContain(own);
		assertThat(l.hardWords()).hasSize(20);
		assertThat(l.hardWords()).extracting(HardWord::word).doesNotContain("it-an-untranslated")
				.startsWith("it-an-hard-1", "it-an-hard-2");
		assertThat(l.hardWords().get(0)).isEqualTo(
				new HardWord("it-an-hard-1", collection, noun, hard, "Hard", 50, 49, 5, 1));
		assertThat(l.hardWords()).extracting(HardWord::topicId).doesNotContain(own);
	}

	// ---- Retention ----

	@Test
	void cohortsFollowVietnamMondayWeeks() { // A-07, A-08, AC-ANL-04
		AdminAnalyticsDTO.Retention before = service.analytics(7).retention();
		LocalDate thisMonday = w.today().with(DayOfWeek.MONDAY);
		LocalDate firstMonday = thisMonday.minusWeeks(7);
		User x = saveUser(true);
		createdAt(x, w.start(firstMonday.plusWeeks(4).plusDays(6)).plus(Duration.ofHours(10))); // Sunday of week 4
		event(x, firstMonday.plusWeeks(5)); // its week 1
		event(x, thisMonday); // its week 3, the current one
		createdAt(saveUser(true), w.start(thisMonday).minusSeconds(1)); // Sunday 23:59:59 → week 6
		createdAt(saveUser(true), w.start(thisMonday)); // Monday 00:00 → week 7
		createdAt(grantAdmin(saveUser(true)), w.start(thisMonday));
		createdAt(saveUser(false), w.start(thisMonday)); // not activated

		AdminAnalyticsDTO.Retention after = service.analytics(7).retention();
		assertThat(after.cohorts()).extracting(AdminAnalyticsDTO.Cohort::weekStart)
				.containsExactlyElementsOf(IntStream.range(0, 8).mapToObj(k -> firstMonday.plusWeeks(k).toString())
						.toList());
		assertThat(IntStream.range(0, 8).mapToLong(k -> after.cohorts().get(k).size() - before.cohorts().get(k).size()))
				.containsExactly(0L, 0L, 0L, 0L, 1L, 0L, 1L, 1L);
		List<Long> c4 = after.cohorts().get(4).active();
		List<Long> c4Before = before.cohorts().get(4).active();
		assertThat(c4).hasSize(4);
		assertThat(IntStream.range(0, 4).mapToLong(i -> c4.get(i) - c4Before.get(i))).containsExactly(0L, 1L, 0L, 1L);
	}

	@Test
	void longestStreaksAccumulateAndSegmentsFollowTheRules() { // A-09, A-10, AC-ANL-05
		AdminAnalyticsDTO.Retention before = service.analytics(7).retention();
		Instant now = Instant.now();
		for (int longest : List.of(1, 3, 3, 45)) {
			studied(saveUser(true), 1, longest, now); // studied now: in no segment (stored streak is never below 1)
		}
		studied(grantAdmin(saveUser(true)), 1, 45, now);
		Instant yesterday = w.start(w.today().minusDays(1)).plus(Duration.ofHours(12));
		studied(saveUser(true), 5, 5, yesterday); // at risk
		studied(saveUser(true), 5, 5, yesterday.minus(Duration.ofDays(1))); // already broken
		studied(saveUser(true), 2, 2, yesterday); // too short to flag
		studied(saveUser(true), 1, 0, now.minus(Duration.ofDays(8))); // idle 8–30
		studied(saveUser(true), 1, 0, now.minus(Duration.ofDays(31))); // gone
		saveUser(true); // never studied
		saveUser(false); // pending email
		studied(grantAdmin(saveUser(true)), 5, 5, yesterday);

		AdminAnalyticsDTO.Retention after = service.analytics(7).retention();
		List<Long> reached = IntStream.range(0, 30)
				.mapToObj(i -> after.longestReached().get(i) - before.longestReached().get(i)).toList();
		// The three non-streak learners above add 5, 5 and 2 to the low buckets too.
		assertThat(reached.subList(0, 6)).containsExactly(7L, 6L, 5L, 3L, 3L, 1L);
		assertThat(reached.get(29)).isEqualTo(1L);
		assertThat(after.longestReached()).isSortedAccordingTo((x, y) -> Long.compare(y, x));
		assertThat(after.segments()).extracting(Segment::id).containsExactly(AdminAnalyticsDTO.SegmentId.values());
		assertThat(IntStream.range(0, 4).mapToLong(i -> after.segments().get(i).count()
				- before.segments().get(i).count())).containsExactly(1L, 1L, 1L, 1L);
	}

	// ---- Economy ----

	@Test
	void coinInByMissionSalesAndCirculation() { // A-11, A-12, A-13, AC-ANL-06
		AdminAnalyticsDTO.Economy before = service.analytics(7).economy();
		for (User u : List.of(a, admin)) {
			reward(u, "IT_AN_ADD5", 1, w.today(), 50);
			reward(u, "IT_AN_ADD5", 1, w.today().minusDays(1), 50);
			long buy = reward(u, "IT_AN_BUY", 2, w.today(), 30);
			rewardRow(u, "MISSION_REWARD:" + buy + ":ITEM", 120, todayStart.plusSeconds(60));
		}
		long boost = shopItem("IT_AN_BOOST");
		for (User u : List.of(a, admin)) {
			for (int i = 0; i < 3; i++) {
				purchase(u, boost, -100, todayStart.plusSeconds(10 + i));
			}
			purchase(u, boost, -100, todayStart.minus(Duration.ofDays(10)));
			purchase(u, boost, -100, todayStart.minus(Duration.ofDays(20)));
		}
		User rich = saveUser(true);
		jdbc.update("UPDATE users SET coin = 100 WHERE id = ?", rich.getId());
		jdbc.update("UPDATE users SET coin = NULL WHERE id = ?", saveUser(true).getId());
		jdbc.update("UPDATE users SET coin = 500 WHERE id = ?", saveUser(false).getId());
		jdbc.update("UPDATE users SET coin = 999 WHERE id = ?", grantAdmin(saveUser(true)).getId());

		AdminAnalyticsDTO.Economy after = service.analytics(7).economy();
		assertThat(after.inByMission()).filteredOn(m -> m.code().startsWith(PREFIX))
				.containsExactly(new MissionCoin("IT_AN_BUY", 150), new MissionCoin("IT_AN_ADD5", 100));
		assertThat(sum(after.inByMission().stream().mapToLong(MissionCoin::coin))
				- sum(before.inByMission().stream().mapToLong(MissionCoin::coin)))
				.isEqualTo(coin(after.daily(), true) - coin(before.daily(), true)).isEqualTo(250);
		assertThat(after.sales()).filteredOn(s -> s.code().startsWith(PREFIX))
				.containsExactly(new ItemSales("IT_AN_BOOST", 3, 300, 1));
		assertThat(sum(after.sales().stream().mapToLong(ItemSales::coin))
				- sum(before.sales().stream().mapToLong(ItemSales::coin)))
				.isEqualTo(coin(after.daily(), false) - coin(before.daily(), false)).isEqualTo(300);
		assertThat(after.circulation() - before.circulation()).isEqualTo(100);
		assertThat(after.adjustments()).isEqualTo(before.adjustments());
	}

	// ---- Scan ----

	@Test
	void funnelReliabilityModelAndRefusals() { // A-14, A-16, A-17
		AdminAnalyticsDTO.Scan empty = service.analytics(7).scan();
		assertThat(empty.modelVersion()).isNull();
		assertThat(empty.latency()).isEqualTo(new AdminAnalyticsDTO.Latency(null, null, null));

		for (User u : List.of(a, admin)) {
			scan(u, "PENDING", null, 0, "m1", 0);
			scan(u, "FAILED", "AI_TIMEOUT", 0, null, 0);
			scan(u, "DONE", null, 0, "m1", 1);
			label(scan(u, "DONE", null, 1, "m1", 2), u, "HIGH", false);
			String withWord = scan(u, "DONE", null, 2, "m2", 4);
			label(withWord, u, "HIGH", true);
			label(withWord, u, "LOW", false);
			saveWordFrom(u, withWord);
			label(scan(u, "DONE", null, 1, "m1", 3), u, "MEDIUM", true);
			scan(u, "FAILED", "QUOTA_EXCEEDED", 0, null, 0);
		}

		AdminAnalyticsDTO.Scan s = service.analytics(7).scan();
		assertThat(s.funnel()).isEqualTo(new Funnel(6, 4, 3, 2, 1L));
		assertThat(s.reliability()).isEqualTo(new Reliability(2, 1, 1));
		assertThat(s.modelVersion()).isEqualTo("m2"); // finished last, across every account
		assertThat(s.quotaHitLearners()).isEqualTo(1);
		assertThat(s.errors()).extracting(e -> e.code().name()).containsExactly("AI_TIMEOUT");
		assertThat(s.latency().p50Ms()).isNotNull();
	}

	@Test
	void savedIsNullUntilAWordCarriesItsScan() { // A-15, AC-ANL-07
		jdbc.update("UPDATE topic_items SET source_scan_id = NULL WHERE source_scan_id IS NOT NULL");
		label(scan(a, "DONE", null, 1, "m1", 1), a, "HIGH", true);

		assertThat(service.analytics(7).scan().funnel()).isEqualTo(new Funnel(1, 1, 1, 1, null));
	}

	// ---- G3 ----

	@Test
	void savingAWordRecordsOnlyTheLearnersOwnFinishedScan() throws Exception { // G3-01…03
		String learner = bearer(a, "ROLE_USER");
		long own = call(learner, "POST", "/api/collections", Map.of("name", "IT AN own " + UUID.randomUUID()), 200)
				.path("data").path("id").asLong();
		long topic = call(learner, "POST", "/api/collections/" + own + "/topics", Map.of("name", "Riêng"), 200)
				.path("data").path("id").asLong();
		String mine = scan(a, "DONE", null, 1, "m1", 1);
		String theirs = scan(b, "DONE", null, 1, "m1", 1);
		String failed = scan(a, "FAILED", "AI_TIMEOUT", 0, null, 0);

		assertThat(sourceOf(saveWord(learner, topic, "it-an-mug", "?scanRequestId=" + mine))).isEqualTo(mine);
		for (String other : List.of(theirs, failed, UUID.randomUUID().toString())) {
			assertThat(sourceOf(saveWord(learner, topic, "it-an-" + UUID.randomUUID(), "?scanRequestId=" + other)))
					.isNull();
		}
		assertThat(sourceOf(saveWord(learner, topic, "it-an-pen", ""))).isNull();

		String token = bearer(admin, "ROLE_USER ROLE_ADMIN");
		long system = call(token, "POST", "/api/collections",
				Map.of("name", "IT AN " + UUID.randomUUID(), "type", "SYSTEM"), 200).path("data").path("id").asLong();
		long systemTopic = call(token, "POST", "/api/collections/" + system + "/topics", Map.of("name", "Hệ thống"),
				200).path("data").path("id").asLong();
		String adminScan = scan(admin, "DONE", null, 1, "m1", 1);
		assertThat(sourceOf(saveWord(token, systemTopic, "it-an-lamp", "?scanRequestId=" + adminScan))).isNull();
	}

	// ---- data ----

	private static long sum(java.util.stream.LongStream s) {
		return s.sum();
	}

	private static long coin(List<CoinDay> daily, boolean in) {
		return daily.stream().mapToLong(d -> in ? d.coinIn() : d.coinOut()).sum();
	}

	private User saveUser(boolean activated) {
		return userRepository.save(User.builder()
				.email(EMAIL + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Tester")
				.activated(activated)
				.coin(0L)
				.build());
	}

	private User grantAdmin(User u) {
		jdbc.update("INSERT INTO user_authorities (user_id, authority_name) VALUES (?, 'ROLE_ADMIN')", u.getId());
		return u;
	}

	private void createdAt(User u, Instant at) {
		jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", UTC.format(at), u.getId());
	}

	private void studied(User u, int streak, int longest, Instant lastStudied) {
		jdbc.update("UPDATE users SET streak_days = ?, longest_streak_days = ?, last_studied_at = ? WHERE id = ?",
				streak, longest, UTC.format(lastStudied), u.getId());
	}

	private void event(User u, LocalDate day) {
		jdbc.update("INSERT INTO learning_events (user_id, event_type, learning_date, source_key, created_at)"
				+ " VALUES (?, 'SRS_REVIEW_COMPLETED', ?, ?, ?)", u.getId(), day, "it-an-" + UUID.randomUUID(),
				UTC.format(w.start(day)));
	}

	/** A bare topic in a new collection; owner null = SYSTEM. */
	private long topic(User owner, String type) {
		String name = "IT AN " + UUID.randomUUID();
		jdbc.update("INSERT INTO collections (name, type, owner_id, created_at, updated_at) VALUES (?, ?, ?, NOW(6),"
				+ " NOW(6))", name, type, owner == null ? null : owner.getId());
		Long collection = jdbc.queryForObject("SELECT id FROM collections WHERE name = ?", Long.class, name);
		jdbc.update("INSERT INTO topics (name, collection_id, created_at, updated_at) VALUES ('IT AN', ?, NOW(6),"
				+ " NOW(6))", collection);
		return jdbc.queryForObject("SELECT MAX(id) FROM topics WHERE collection_id = ?", Long.class, collection);
	}

	private long item(long topic) {
		jdbc.update("INSERT INTO topic_items (topic_id, created_at) VALUES (?, NOW(6))", topic);
		return jdbc.queryForObject("SELECT MAX(id) FROM topic_items WHERE topic_id = ?", Long.class, topic);
	}

	private void card(User u, long item, int state, float stability, Instant due, int lapses) {
		jdbc.update("INSERT INTO fsrs_records (user_id, topic_item_id, state, stability, difficulty, due, lapses, reps,"
				+ " created_at, updated_at) VALUES (?, ?, ?, ?, 5, ?, ?, 1, NOW(6), NOW(6))", u.getId(), item, state,
				// As FsrsRecord writes it: a UTC LocalDateTime through the JDBC timestamp binding (see A-03 below).
				stability, Timestamp.valueOf(LocalDateTime.ofInstant(due, ZoneOffset.UTC)), lapses);
	}

	private long word(String token, long topic, String word, String translation) throws Exception {
		Map<String, Object> main = translation == null ? Map.of("word", word)
				: Map.of("word", word, "translation", translation);
		return call(token, "POST", "/api/topics/" + topic + "/items", Map.of("groups", Map.of("main", main)), 201)
				.path("data").path("topicItemId").asLong();
	}

	/** {item, outcome} pairs: {@code correct}, then {@code wrong}, then {@code open} (UNCOMMITTED). */
	private static List<Object[]> outcomes(long item, int correct, int wrong, int open) {
		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < correct + wrong + open; i++) {
			rows.add(new Object[] {item, i < correct ? "CORRECT" : i < correct + wrong ? "INCORRECT" : "UNCOMMITTED"});
		}
		return rows;
	}

	private void quiz(User u, long topic, String status, List<Object[]> answers) {
		String key = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO quizzes (user_id, topic_id, topic_name, mode, status, requested_count,"
				+ " create_idempotency_key, created_at, updated_at, completed_at) VALUES (?, ?, 'IT AN', 'MCQ', ?, 5, ?,"
				+ " NOW(6), NOW(6), ?)", u.getId(), topic, status, key, UTC.format(todayStart.plusSeconds(30)));
		Long quiz = jdbc.queryForObject("SELECT id FROM quizzes WHERE create_idempotency_key = ?", Long.class, key);
		for (int i = 0; i < answers.size(); i++) {
			jdbc.update("INSERT INTO quiz_questions (quiz_id, position, prompt, correct_answer, outcome, attempt_count,"
					+ " resolved, topic_item_id) VALUES (?, ?, 'p', 'a', ?, 1, TRUE, ?)", quiz, i + 1, answers.get(i)[1],
					answers.get(i)[0]);
		}
	}

	/** A claimed mission of {@code code} on {@code day} paying {@code coin}; returns the user_daily_missions id. */
	private long reward(User u, String code, int slot, LocalDate day, long coin) {
		jdbc.update("INSERT IGNORE INTO mission_templates (code, name, description, category, trigger_event,"
				+ " target_value, reward_coin, reward_xp, created_at, updated_at) VALUES (?, 'IT', 'IT', 'SRS',"
				+ " 'SRS_REVIEW_COMPLETED', 1, 10, 0, NOW(6), NOW(6))", code);
		Long template = jdbc.queryForObject("SELECT id FROM mission_templates WHERE code = ?", Long.class, code);
		Instant at = w.start(day).plusSeconds(60);
		jdbc.update("INSERT INTO user_daily_missions (user_id, mission_template_id, mission_date, slot, code, name,"
				+ " description, category, trigger_event, target_value, reward_coin, reward_xp, current_progress, status,"
				+ " completed_at, claimed_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'IT', 'IT', 'SRS',"
				+ " 'SRS_REVIEW_COMPLETED', 1, 10, 0, 1, 'CLAIMED', ?, ?, NOW(6), NOW(6))", u.getId(), template, day,
				slot, code, UTC.format(at), UTC.format(at));
		long id = jdbc.queryForObject("SELECT id FROM user_daily_missions WHERE user_id = ? AND mission_date = ? AND"
				+ " slot = ?", Long.class, u.getId(), day, slot);
		rewardRow(u, "MISSION_REWARD:" + id, coin, at);
		return id;
	}

	private void rewardRow(User u, String eventKey, long coin, Instant at) {
		jdbc.update("INSERT INTO coin_transactions (user_id, source_type, amount, balance_after, event_key, created_at)"
				+ " VALUES (?, 'MISSION_REWARD', ?, 0, ?, ?)", u.getId(), coin, eventKey, UTC.format(at));
	}

	private long shopItem(String code) {
		jdbc.update("INSERT INTO shop_items (code, name, type, status, purchasable, sort_order, version, created_at,"
				+ " updated_at) VALUES (?, 'IT', 'XP_BOOSTER', 'PUBLISHED', TRUE, 0, 0, NOW(6), NOW(6))", code);
		return jdbc.queryForObject("SELECT id FROM shop_items WHERE code = ?", Long.class, code);
	}

	private void purchase(User u, long item, long amount, Instant at) {
		jdbc.update("INSERT INTO coin_transactions (user_id, source_type, amount, balance_after, reference_id,"
				+ " event_key, created_at) VALUES (?, 'SHOP_PURCHASE', ?, 0, ?, ?, ?)", u.getId(), amount,
				Long.toString(item), "it-an-" + UUID.randomUUID(), UTC.format(at));
	}

	/** A scan created today; DONE ones finish {@code finishedAfterSec} seconds after the start. */
	private String scan(User u, String status, String errorCode, int labels, String model, int finishedAfterSec) {
		String id = UUID.randomUUID().toString();
		Instant started = todayStart.plusSeconds(1);
		boolean done = "DONE".equals(status);
		jdbc.update("INSERT INTO scan_requests (id, user_id, status, error_code, detection_count, model_version,"
				+ " result_json, created_at, updated_at, started_at, finished_at, version)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
				id, u.getId(), status, errorCode, done ? labels : null, done ? model : null,
				done ? "{\"imageWidth\":1,\"imageHeight\":1,\"detections\":[]}" : null, UTC.format(todayStart),
				UTC.format(todayStart), UTC.format(started),
				done ? UTC.format(started.plusSeconds(finishedAfterSec)) : null);
		return id;
	}

	private void label(String scanId, User u, String reliability, boolean hasWord) {
		jdbc.update("INSERT INTO scan_detections (scan_request_id, user_id, created_at, label, reliability, has_word)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", scanId, u.getId(), UTC.format(todayStart), "it-an-" + UUID.randomUUID(),
				reliability, hasWord);
	}

	private void saveWordFrom(User u, String scanId) {
		jdbc.update("UPDATE topic_items SET source_scan_id = ? WHERE id = ?", scanId, item(topic(u, "USER")));
	}

	private long saveWord(String token, long topic, String word, String query) throws Exception {
		return call(token, "POST", "/api/topics/" + topic + "/items" + query,
				Map.of("groups", Map.of("main", Map.of("word", word, "translation", "nghĩa"))), 201)
				.path("data").path("topicItemId").asLong();
	}

	private String sourceOf(long item) {
		return jdbc.queryForObject("SELECT source_scan_id FROM topic_items WHERE id = ?", String.class, item);
	}

	private JsonNode get(String token, String query, int expected) throws Exception {
		return call(token, "GET", "/api/admin/analytics" + query, null, expected);
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
