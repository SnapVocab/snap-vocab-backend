package vn.ptit.snapvocab.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.badge.service.BadgeService;
import vn.ptit.snapvocab.config.DataInitializer;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.UserDailyMission;
import vn.ptit.snapvocab.mission.domain.dto.DailyMissionsDTO;
import vn.ptit.snapvocab.mission.domain.dto.MissionClaimResultDTO;
import vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionErrorCode;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.exception.MissionException;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;
import vn.ptit.snapvocab.mission.repository.UserDailyMissionRepository;
import vn.ptit.snapvocab.mission.repository.UserDailyMissionClaimRepository;
import vn.ptit.snapvocab.progress.service.ProgressEventListener;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;
import vn.ptit.snapvocab.quiz.domain.Quiz;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;
import vn.ptit.snapvocab.quiz.domain.dto.CheckQuestionRequest;
import vn.ptit.snapvocab.quiz.domain.dto.CreateQuizRequest;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver;
import vn.ptit.snapvocab.quiz.service.ListeningWordPool;
import vn.ptit.snapvocab.quiz.service.QuizGenerator;
import vn.ptit.snapvocab.quiz.service.QuizService;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.shop.service.InventoryService;
import vn.ptit.snapvocab.shop.service.ShopMapper;
import vn.ptit.snapvocab.shop.service.ShopRewardService;
import vn.ptit.snapvocab.shop.service.ShopService;
import vn.ptit.snapvocab.shop.service.WalletService;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.config.SemanticBindingMigration;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicItemDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.vocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.vocab.domain.mapper.TopicSchemaMapper;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.vocab.service.TopicService;
import vn.ptit.snapvocab.xp.service.XpService;

/**
 * Daily Mission (daily_mission.md §4, §6, §8, §13) end-to-end through the real producers against LOCAL MySQL:
 * real row locks, isolation, UNIQUE/CHECK constraints and rollback. No H2.
 */
@LocalMySqlJpaTest
@Import({ DataInitializer.class, SemanticBindingMigration.class, QuizService.class, QuizContentResolver.class,
		QuizGenerator.class, ListeningWordPool.class, XpService.class, TopicService.class, ShopService.class, InventoryService.class,
		WalletService.class, ShopRewardService.class, ShopMapper.class, MissionTracker.class, DailyMissionService.class,
		ProgressService.class, StreakFreezeService.class, ProgressEventListener.class, BadgeService.class,
		DailyMissionIntegrationTest.Beans.class })
class DailyMissionIntegrationTest {

	private static final String CORE_QUIZ_PERFECT = "PERFECT_MINI_QUIZ";

	@TestConfiguration
	static class Beans {
		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder(4);
		}

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}

	// addTopicItem uses none of the mappers; StorageService is only needed to build ShopMapper.
	@MockitoBean
	TopicMapper topicMapper;
	@MockitoBean
	TopicSchemaMapper topicSchemaMapper;
	@MockitoBean
	SchemaMapper schemaMapper;
	@MockitoBean
	StorageService storageService;
	@MockitoSpyBean
	UserDailyMissionClaimRepository claimRepository;
	@MockitoSpyBean
	MissionTracker missionTracker;
	@Autowired
	MeterRegistry meterRegistry;
	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	DailyMissionService dailyMissionService;
	@Autowired
	AdminAuditService adminAuditService;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	QuizService quizService;
	@Autowired
	TopicService topicService;
	@Autowired
	ShopService shopService;
	@Autowired
	InventoryService inventoryService;
	@Autowired
	MissionTemplateRepository templateRepository;
	@Autowired
	UserDailyMissionRepository missionRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	ShopItemRepository shopItemRepository;
	@Autowired
	UserItemRepository userItemRepository;
	@Autowired
	CollectionRepository collectionRepository;
	@Autowired
	TopicRepository topicRepository;
	@Autowired
	SchemaRepository schemaRepository;
	@Autowired
	TemplateRepository templateLayoutRepository;
	@Autowired
	QuizRepository quizRepository;
	@Autowired
	DataSource dataSource;
	@Autowired
	PlatformTransactionManager transactionManager;

	/** Not a bean here: as an ApplicationRunner it would seed at startup. */
	private MissionCatalogSeeder seeder() {
		return new MissionCatalogSeeder(templateRepository, adminAuditService, objectMapper, transactionManager);
	}

	@AfterEach
	void cleanUp() {
		SecurityContextHolder.clearContext();
		JdbcTemplate jdbc = jdbc();
		for (String table : List.of("user_badges", "user_badge_word_credits", "user_word_credits", "user_daily_mission_claims", "user_daily_mission_progress_logs",
				"user_daily_missions", "mission_templates", "learning_events", "quiz_matching_attempts",
				"quiz_question_options", "quiz_matching_tiles", "quiz_questions", "quizzes",
				"topic_item_attribute_values", "topic_item_attribute_groups", "topic_items", "topics", "collections",
				"experience_logs", "booster_activations", "coin_transactions", "user_items", "shop_items", "scan_requests")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'dm-it-%'");
	}

	// --- Assignment (§4, AC §13.1) ---------------------------------------------------------------

	@Test
	void zeroStateLearnerGetsTheFiveFeasibleMissionsOnce() {
		catalog();
		User user = newUser(0);

		DailyMissionsDTO first = dailyMissionService.getToday(user.getId());
		DailyMissionsDTO again = dailyMissionService.getToday(user.getId());

		assertThat(first.missions()).extracting(DailyMissionsDTO.Mission::slot).containsExactly(1, 2, 3, 4, 5);
		assertThat(first.missions()).extracting(DailyMissionsDTO.Mission::code).containsExactlyInAnyOrder(
				"ADD_5_WORDS", "QUIZ_SCORE_80", "FINISH_2_SHORT_QUIZZES", CORE_QUIZ_PERFECT, "KEEP_DAILY_STREAK");
		assertThat(first.missions().get(4).code()).isEqualTo("KEEP_DAILY_STREAK");
		assertThat(first.missions()).allSatisfy(m -> {
			assertThat(m.status()).isEqualTo(DailyMissionStatus.IN_PROGRESS);
			assertThat(m.currentProgress()).isZero();
		});
		assertThat(again.missions()).extracting(DailyMissionsDTO.Mission::id)
				.containsExactlyElementsOf(first.missions().stream().map(DailyMissionsDTO.Mission::id).toList());
		assertThat(first.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(first.resetAt()).isEqualTo(first.missionDate().plusDays(1).atStartOfDay(MissionTracker.ZONE).toInstant());
	}

	@Test
	void parallelFirstTriggersCreateExactlyOneSetOfFive() throws Exception {
		catalog();
		User user = newUser(0);
		Topic topic = userTopic(user);

		List<Object> results = runConcurrently(6, i -> {
			if (i % 2 == 0) {
				return dailyMissionService.getToday(user.getId()).missions().size();
			}
			authenticate(user);
			return topicService.addTopicItem(topic.getId(), word("parallel" + i, "song song " + i)).getTopicItemId();
		});

		assertThat(results).doesNotContainNull();
		assertThat(count("SELECT COUNT(*) FROM user_daily_missions WHERE user_id = ?", user.getId())).isEqualTo(5);
		assertThat(count("SELECT COUNT(DISTINCT mission_template_id) FROM user_daily_missions WHERE user_id = ?",
				user.getId())).isEqualTo(5);
		assertThat(progress(user).get("ADD_5_WORDS")).isEqualTo(3);
	}

	@Test
	void softPreferenceAvoidsYesterdaysTemplateWhenTheSlotHasAnAlternative() {
		catalog();
		templateRepository.save(template("IT_RETENTION_ALT", MissionCategory.RETENTION,
				MissionTriggerEvent.LEARNING_ACTIVITY_RECORDED));
		User user = newUser(0);
		LocalDate today = MissionTracker.missionDate(Economy.now());
		tx().executeWithoutResult(s -> {
			Economy.lockUser(userRepository, user.getId());
			missionTracker.ensureAssigned(user.getId(), today.minusDays(1), Economy.now());
		});
		String yesterdaySlot5 = jdbc().queryForObject(
				"SELECT code FROM user_daily_missions WHERE user_id = ? AND mission_date = ? AND slot = 5", String.class,
				user.getId(), today.minusDays(1));

		DailyMissionsDTO todays = dailyMissionService.getToday(user.getId());

		assertThat(todays.missions().get(4).code()).isNotEqualTo(yesterdaySlot5)
				.isIn("KEEP_DAILY_STREAK", "IT_RETENTION_ALT");
	}

	@Test
	void resourceMissionsAreOnlyAssignedWhenEligible() {
		catalog();
		deactivate("KEEP_DAILY_STREAK");
		ShopItem booster = shopItem(ShopItemType.XP_BOOSTER, 100);
		User broke = newUser(0);
		User owner = newUser(0);
		grant(owner, booster, 1);

		assertThatThrownBy(() -> dailyMissionService.getToday(broke.getId()))
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getCode()).isEqualTo(MissionErrorCode.MISSION_POOL_INSUFFICIENT));
		// Booster owned and idle, but nothing affordable: slot 5 can only be USE_XP_BOOSTER.
		assertThat(dailyMissionService.getToday(owner.getId()).missions().get(4).code()).isEqualTo("USE_XP_BOOSTER");
	}

	@Test
	void hasPurchasableItemCountsStreakFreezeUnitsAfterThePendingSettlement() {
		catalog();
		deactivate("KEEP_DAILY_STREAK", "USE_XP_BOOSTER");
		ShopItem freeze = shopItemRepository.save(ShopItem.builder().code("IT_FREEZE").type(ShopItemType.STREAK_FREEZE)
				.name("Freeze").status(ShopItemStatus.PUBLISHED).purchasable(true).price(200L).maxQuantity(2).build());
		// Held 2 of 2, but yesterday was missed: one unit is already spent, so a second one can be bought.
		User user = newUser(1000);
		user.setStreakDays(5);
		user.setLastStudiedAt(MissionTracker.missionDate(Instant.now()).minusDays(2).atTime(12, 0)
				.atZone(ProgressService.ZONE).toInstant());
		userRepository.save(user);
		grant(user, freeze, 2);

		assertThat(dailyMissionService.getToday(user.getId()).missions().get(4).code()).isEqualTo("BUY_SHOP_ITEM");
	}

	@Test
	void activatingTheLastBoosterAsFirstTriggerAssignsAndCompletesUseXpBooster() {
		catalog();
		deactivate("KEEP_DAILY_STREAK", "BUY_SHOP_ITEM");
		ShopItem booster = shopItem(ShopItemType.XP_BOOSTER, 100);
		User user = newUser(0);
		grant(user, booster, 1);

		inventoryService.activate(user.getId(), booster.getId(), "act-1");

		assertThat(status(user, "USE_XP_BOOSTER")).isEqualTo("COMPLETED");
		assertThat(sourceKeys(user, "USE_XP_BOOSTER")).singleElement().asString().startsWith("BOOSTER_USED:");
	}

	@Test
	void purchaseAsFirstTriggerAssignsAndCompletesBuyShopItem() {
		catalog();
		deactivate("KEEP_DAILY_STREAK", "USE_XP_BOOSTER");
		ShopItem frame = shopItem(ShopItemType.AVATAR_FRAME, 50);
		User user = newUser(100);

		shopService.purchase(user.getId(), frame.getId(), 50L, "buy-1");
		shopService.purchase(user.getId(), frame.getId(), 50L, "buy-1");

		assertThat(status(user, "BUY_SHOP_ITEM")).isEqualTo("COMPLETED");
		assertThat(sourceKeys(user, "BUY_SHOP_ITEM")).hasSize(1);
		assertThat(coin(user)).isEqualTo(50);
	}

	@Test
	void insufficientPoolWritesNothingAnswers503AndNeverBlocksTheAction() {
		catalog();
		deactivate("PERFECT_MINI_QUIZ", "FINISH_2_SHORT_QUIZZES");
		User user = newUser(0);
		Topic topic = userTopic(user);
		authenticate(user);
		double alertsBefore = poolInsufficientCount();

		Long itemId = topicService.addTopicItem(topic.getId(), word("orphan", "mồ côi")).getTopicItemId();

		assertThat(itemId).isNotNull();
		assertThat(count("SELECT COUNT(*) FROM topic_items WHERE id = ?", itemId)).isEqualTo(1);
		assertThatThrownBy(() -> dailyMissionService.getToday(user.getId()))
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getCode()).isEqualTo(MissionErrorCode.MISSION_POOL_INSUFFICIENT));
		assertThat(count("SELECT COUNT(*) FROM user_daily_missions WHERE user_id = ?", user.getId())).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_word_credits WHERE user_id = ?", user.getId())).isZero();
		assertThat(poolInsufficientCount() - alertsBefore).as("one per failed assignment: add-item + GET").isEqualTo(2);
	}

	// --- Tracking (§5.5, §6, AC §13.2) -----------------------------------------------------------

	@Test
	void perfectFiveQuestionQuizAdvancesExactlyTheMatchingMissionsAndReplayAddsNothing() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);

		Long quizId = completeQuiz(user, topic, 5, 5);
		quizService.complete(quizId);

		Map<String, Integer> progress = progress(user);
		assertThat(status(user, "QUIZ_SCORE_80")).isEqualTo("COMPLETED");
		assertThat(status(user, CORE_QUIZ_PERFECT)).isEqualTo("COMPLETED");
		assertThat(status(user, "KEEP_DAILY_STREAK")).isEqualTo("COMPLETED");
		assertThat(progress.get("FINISH_2_SHORT_QUIZZES")).isEqualTo(1);
		assertThat(sourceKeys(user, "QUIZ_SCORE_80")).containsExactly("QUIZ_COMPLETED:" + quizId);
		assertThat(sourceKeys(user, "KEEP_DAILY_STREAK")).singleElement().asString().startsWith("LEARNING:");
		assertThat(count("SELECT COUNT(*) FROM user_daily_mission_progress_logs")).isEqualTo(5 + 4);
	}

	@Test
	void eightyPercentCountsForScore80ButNotPerfect() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);

		completeQuiz(user, topic, 5, 4);

		assertThat(status(user, "QUIZ_SCORE_80")).isEqualTo("COMPLETED");
		assertThat(status(user, CORE_QUIZ_PERFECT)).isEqualTo("IN_PROGRESS");
		assertThat(progress(user).get("FINISH_2_SHORT_QUIZZES")).isEqualTo(1);
	}

	@Test
	void perfectTenQuestionQuizAdvancesOnlyScore80() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 10);

		completeQuiz(user, topic, 10, 10);

		assertThat(status(user, "QUIZ_SCORE_80")).isEqualTo("COMPLETED");
		assertThat(status(user, CORE_QUIZ_PERFECT)).isEqualTo("IN_PROGRESS");
		assertThat(progress(user).get("FINISH_2_SHORT_QUIZZES")).isZero();
	}

	@Test
	void addWordCountsQuizReadyNewWordsOnceForLife() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopic(user);
		Topic other = userTopic(user);
		authenticate(user);

		topicService.addTopicItem(topic.getId(), word("Apple", "táo"));
		topicService.addTopicItem(topic.getId(), word("  aPPLE  ", "quả táo"));
		topicService.addTopicItem(topic.getId(), word("banana", null));
		topicService.addTopicItem(topic.getId(), word("New   York", "Niu Oóc"));
		topicService.addTopicItem(topic.getId(), word("new york", "Niu Oóc"));
		topicService.addTopicItem(topic.getId(), word("cherry", "anh đào"));
		topicService.addTopicItem(topic.getId(), word("cherry", "anh đào"));
		topicService.addTopicItem(other.getId(), word("apple", "táo"));

		assertThat(count("SELECT COUNT(*) FROM topic_items")).isEqualTo(8);
		assertThat(progress(user).get("ADD_5_WORDS")).isEqualTo(3);
		assertThat(count("SELECT COUNT(*) FROM user_word_credits WHERE user_id = ?", user.getId())).isEqualTo(3);
	}

	@Test
	void scanSaveCreditsOnlyWordsTheLearnersOwnFinishedScanDetectedOncePerScan() { // PD-DM-07, §6.1
		catalog();
		jdbc().update("UPDATE mission_templates SET is_active = 1 WHERE code = 'SCAN_SAVE_3_WORDS'");
		User user = newUser(0);
		User other = newUser(0);
		Topic topic = userTopic(user);
		Topic second = userTopic(user);
		String detections = "[" + detection("coffee mug", "\"mug\"") + "," + detection("Laptop", "null") + "]";
		String scan = scan(user, "DONE", detections);
		String foreign = scan(other, "DONE", detections);
		String unfinished = scan(user, "PROCESSING", null);
		authenticate(user);

		topicService.addTopicItem(topic.getId(), word("Mug", "cốc"), scan); // headword
		topicService.addTopicItem(second.getId(), word("mug", "cái cốc"), scan); // same word, same scan
		topicService.addTopicItem(topic.getId(), word("laptop", "máy tính"), scan); // label, any case
		topicService.addTopicItem(topic.getId(), word("banana", "chuối"), scan); // not detected
		topicService.addTopicItem(topic.getId(), word("laptop", "máy tính xách tay"), foreign);
		topicService.addTopicItem(topic.getId(), word("laptop", "laptop"), unfinished);
		topicService.addTopicItem(topic.getId(), word("laptop", "máy xách tay"), "no-such-scan");

		// Every word is saved; only the two detected ones count, and slot 1 is the Scan mission.
		assertThat(count("SELECT COUNT(*) FROM topic_items")).isEqualTo(7);
		assertThat(progress(user).get("SCAN_SAVE_3_WORDS")).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM user_daily_missions WHERE user_id = ? AND code = 'SCAN_SAVE_3_WORDS' AND slot = 1",
				user.getId())).isEqualTo(1);
		// The same two credited saves are learning activity (PO 2026-10-22): streak and KEEP_DAILY_STREAK.
		assertThat(count("SELECT COUNT(*) FROM learning_events WHERE user_id = ? AND event_type = 'WORD_SCANNED_AND_SAVED'",
				user.getId())).isEqualTo(2);
		assertThat(userRepository.findById(user.getId()).orElseThrow().getStreakDays()).isEqualTo(1);
		assertThat(status(user, "KEEP_DAILY_STREAK")).isEqualTo("COMPLETED");
	}

	@Test
	void weeklyReportRatesEachCodePerLearnerStageAndSkipsToday() throws Exception { // PD-DM-14
		catalog();
		User user = newUser(0);
		LocalDate today = MissionTracker.missionDate(Instant.now());
		MissionTemplate add = templateRepository.findByCode("ADD_5_WORDS").orElseThrow();
		MissionTemplate quiz = templateRepository.findByCode("QUIZ_SCORE_80").orElseThrow();
		pastMission(user, add, today.minusDays(8), 1, "CLAIMED"); // the learner's first mission day
		pastMission(user, add, today.minusDays(1), 1, "COMPLETED"); // completed, never claimed: expired
		pastMission(user, quiz, today.minusDays(1), 2, "IN_PROGRESS");
		pastMission(user, add, today, 1, "IN_PROGRESS"); // still running: not reported

		String sql = Files.readString(Path.of("reports/daily_mission_report.sql")).trim();
		List<Map<String, Object>> rows = jdbc().queryForList(sql.substring(0, sql.length() - 1));

		assertThat(rows).extracting(row -> row.get("code") + " " + row.get("stage") + " " + row.get("assigned") + " "
				+ row.get("completed_pct") + " " + row.get("claimed_pct") + " " + row.get("expired_pct"))
				.containsExactly(
						"ADD_5_WORDS 1. Ngày đầu 1 100.0 100.0 0.0",
						"ADD_5_WORDS 3. Sau tuần đầu 1 100.0 0.0 100.0",
						"QUIZ_SCORE_80 3. Sau tuần đầu 1 0.0 0.0 0.0");
	}

	private void pastMission(User user, MissionTemplate template, LocalDate date, int slot, String status) {
		Long id = missionRepository.save(UserDailyMission.assign(user, template, date, slot)).getId();
		if (!status.equals("IN_PROGRESS")) {
			jdbc().update("UPDATE user_daily_missions SET status = ?, current_progress = target_value, completed_at = NOW(6),"
					+ " claimed_at = IF(? = 'CLAIMED', NOW(6), NULL) WHERE id = ?", status, status, id);
		}
	}

	@Test
	void wordsAddedOnADayWithoutAnAddWordMissionLeaveNoCredit() {
		catalog();
		deactivate("ADD_5_WORDS");
		templateRepository.save(template("IT_EXTRA_QUIZ", MissionCategory.QUIZ, MissionTriggerEvent.QUIZ_COMPLETED));
		User user = newUser(0);
		Topic topic = userTopic(user);
		authenticate(user);

		topicService.addTopicItem(topic.getId(), word("lonely", "cô đơn"));

		assertThat(progress(user)).doesNotContainKey("ADD_5_WORDS").hasSize(5);
		assertThat(count("SELECT COUNT(*) FROM user_word_credits WHERE user_id = ?", user.getId())).isZero();
	}

	@Test
	void wordsOutsideTheLearnersOwnUserCollectionNeverCount() {
		catalog();
		User user = newUser(0);
		Topic system = topic(collectionRepository.save(Collection.builder().name("sys").type(CollectionType.SYSTEM)
				.build()));
		dailyMissionService.getToday(user.getId());
		authenticateAdmin(user);

		topicService.addTopicItem(system.getId(), word("system", "hệ thống"));

		assertThat(progress(user).get("ADD_5_WORDS")).isZero();
	}

	/**
	 * actionInstant before the reset, commit after it (P7, AC §13.2): the quiz captures 23:59:59 and is then held inside
	 * its transaction (still owning the users lock) while a GET issued at 00:00:01 waits on that lock. Each thread has
	 * its own controlled clock. The quiz credit lands on the old day; the GET, served after the commit, gets a fresh set.
	 */
	@Test
	void actionBeforeResetCommittedAfterResetCreditsTheOldDayAndCannotBeClaimed() throws Exception {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		LocalDate day = MissionTracker.missionDate(Instant.now());
		Instant beforeReset = LocalDateTime.of(day, LocalTime.of(23, 59, 59)).atZone(MissionTracker.ZONE).toInstant();
		Instant afterReset = beforeReset.plusSeconds(2);
		Long quizId = startQuiz(user, topic, 5);
		answer(quizId, 5);
		CountDownLatch actionCaptured = new CountDownLatch(1);
		CountDownLatch releaseCommit = new CountDownLatch(1);
		doAnswer(inv -> {
			if (inv.getArgument(2) == MissionTriggerEvent.QUIZ_COMPLETED
					&& Thread.currentThread().getName().equals("late-quiz")) {
				actionCaptured.countDown();
				assertThat(releaseCommit.await(30, TimeUnit.SECONDS)).isTrue();
			}
			return inv.callRealMethod();
		}).when(AopTestUtils.<MissionTracker>getUltimateTargetObject(missionTracker))
				.apply(any(), any(), any(), any(), any(), any());

		AtomicReference<Throwable> quizFailure = new AtomicReference<>();
		Thread quiz = new Thread(() -> {
			authenticate(user);
			try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
				clock.when(Economy::now).thenReturn(beforeReset);
				quizService.complete(quizId);
			} catch (Throwable e) {
				quizFailure.set(e);
			} finally {
				SecurityContextHolder.clearContext();
			}
		}, "late-quiz");
		quiz.start();
		assertThat(actionCaptured.await(30, TimeUnit.SECONDS)).isTrue();

		FutureTask<DailyMissionsDTO> afterResetGet = new FutureTask<>(() -> {
			try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
				clock.when(Economy::now).thenReturn(afterReset);
				return dailyMissionService.getToday(user.getId());
			}
		});
		new Thread(afterResetGet, "after-reset-get").start();
		awaitUsersLockWait();
		assertThat(afterResetGet.isDone()).as("GET after reset waits for the quiz transaction").isFalse();
		releaseCommit.countDown();
		quiz.join(30_000);
		DailyMissionsDTO next = afterResetGet.get(30, TimeUnit.SECONDS);

		assertThat(quizFailure.get()).isNull();
		assertThat(statusOn(user, day, "QUIZ_SCORE_80")).isEqualTo("COMPLETED");
		assertThat(jdbc().queryForObject("SELECT completed_at FROM user_daily_missions WHERE id = ?",
				java.sql.Timestamp.class, missionId(user, day, "QUIZ_SCORE_80")).toInstant()).isEqualTo(beforeReset);
		assertThat(jdbc().queryForObject("SELECT learning_date FROM learning_events WHERE source_key = ?",
				LocalDate.class, "QUIZ_COMPLETED:" + quizId)).isEqualTo(day);
		assertThat(next.missionDate()).isEqualTo(day.plusDays(1));
		assertThat(next.missions()).allSatisfy(m -> assertThat(m.currentProgress()).isZero());
		try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
			clock.when(Economy::now).thenReturn(afterReset);
			assertThat(codeOf(() -> dailyMissionService.claim(user.getId(), missionId(user, day, "QUIZ_SCORE_80"), "late")))
					.isEqualTo(MissionErrorCode.MISSION_EXPIRED);
		}
		assertThat(count("SELECT COUNT(*) FROM user_daily_mission_claims")).isZero();
		assertThat(count("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", user.getId())).isZero();
	}

	// --- Claim (§8.2, AC §13.3) ------------------------------------------------------------------

	@Test
	void claimPaysTheSnapshotOnceAndEveryRetryReplays() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long score80 = missionId(user, "QUIZ_SCORE_80");
		long expBefore = exp(user);

		MissionClaimResultDTO claimed = dailyMissionService.claim(user.getId(), score80, "claim-1");
		MissionClaimResultDTO lostResponseRetry = dailyMissionService.claim(user.getId(), score80, "claim-1");
		MissionClaimResultDTO otherKey = dailyMissionService.claim(user.getId(), score80, "claim-2");

		assertThat(claimed.replayed()).isFalse();
		assertThat(claimed.status()).isEqualTo(DailyMissionStatus.CLAIMED);
		assertThat(claimed.rewardGranted()).isEqualTo(new MissionClaimResultDTO.Granted(20, 40, null, null, 0, 0));
		assertThat(claimed.coinBalance()).isEqualTo(20);
		assertThat(claimed.totalXp()).isEqualTo(expBefore + 40);
		assertThat(lostResponseRetry.replayed()).isTrue();
		assertThat(lostResponseRetry.rewardGranted()).isEqualTo(claimed.rewardGranted());
		assertThat(otherKey.replayed()).isTrue();
		assertThat(coin(user)).isEqualTo(20);
		assertThat(exp(user)).isEqualTo(expBefore + 40);
		assertThat(jdbc().queryForMap("SELECT * FROM coin_transactions WHERE user_id = ?", user.getId()))
				.containsEntry("source_type", "MISSION_REWARD")
				.containsEntry("event_key", "MISSION_REWARD:" + score80)
				.containsEntry("reference_id", score80.toString());
		assertThat(count("SELECT COUNT(*) FROM experience_logs WHERE event_key = ?", "MISSION_REWARD:" + score80))
				.isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM user_daily_mission_claims")).isEqualTo(1);
	}

	@Test
	void claimRejectsWhatIsNotClaimable() {
		catalog();
		User user = newUser(0);
		User intruder = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long score80 = missionId(user, "QUIZ_SCORE_80");
		Long perfect = missionId(user, CORE_QUIZ_PERFECT);
		Long finish2 = missionId(user, "FINISH_2_SHORT_QUIZZES");
		dailyMissionService.claim(user.getId(), score80, "k1");

		assertThat(codeOf(() -> dailyMissionService.claim(user.getId(), perfect, "k1")))
				.isEqualTo(MissionErrorCode.IDEMPOTENCY_KEY_REUSED);
		assertThat(codeOf(() -> dailyMissionService.claim(user.getId(), finish2, "k2")))
				.isEqualTo(MissionErrorCode.MISSION_NOT_COMPLETED);
		assertThat(codeOf(() -> dailyMissionService.claim(intruder.getId(), perfect, "k3")))
				.isEqualTo(MissionErrorCode.MISSION_NOT_FOUND);
		assertThatThrownBy(() -> dailyMissionService.claim(user.getId(), perfect, null))
				.isInstanceOf(ShopException.class).hasMessageContaining("Idempotency-Key");
		assertThat(status(user, CORE_QUIZ_PERFECT)).isEqualTo("COMPLETED");
		assertThat(coin(user)).isEqualTo(20);
	}

	@Test
	void parallelClaimsWithSameAndDifferentKeysPayExactlyOnce() throws Exception {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long perfect = missionId(user, CORE_QUIZ_PERFECT);
		long expBefore = exp(user);

		List<MissionClaimResultDTO> results = runConcurrently(8,
				i -> dailyMissionService.claim(user.getId(), perfect, i % 2 == 0 ? "same" : "key-" + i));

		assertThat(results).allSatisfy(r -> assertThat(r.status()).isEqualTo(DailyMissionStatus.CLAIMED));
		assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
		assertThat(coin(user)).isEqualTo(25);
		assertThat(exp(user)).isEqualTo(expBefore + 45);
		assertThat(count("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", user.getId())).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM user_daily_mission_claims")).isEqualTo(1);
	}

	@Test
	void failureAfterRewardsRollsBackEverythingAndTheRetryPaysOnce() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long score80 = missionId(user, "QUIZ_SCORE_80");
		long expBefore = exp(user);
		doThrow(new IllegalStateException("injected after coin and XP")).when(claimRepository).save(any());

		assertThatThrownBy(() -> dailyMissionService.claim(user.getId(), score80, "boom"))
				.hasMessageContaining("injected");
		assertThat(status(user, "QUIZ_SCORE_80")).isEqualTo("COMPLETED");
		assertThat(coin(user)).isZero();
		assertThat(exp(user)).isEqualTo(expBefore);
		assertThat(count("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", user.getId())).isZero();
		assertThat(count("SELECT COUNT(*) FROM experience_logs WHERE event_key = ?", "MISSION_REWARD:" + score80))
				.isZero();

		Mockito.reset(claimRepository);
		MissionClaimResultDTO retry = dailyMissionService.claim(user.getId(), score80, "boom");

		assertThat(retry.replayed()).isFalse();
		assertThat(coin(user)).isEqualTo(20);
		assertThat(exp(user)).isEqualTo(expBefore + 40);
	}

	@Test
	void missionXpIsNeverBoosted() {
		catalog();
		ShopItem booster = shopItem(ShopItemType.XP_BOOSTER, 100);
		User user = newUser(0);
		grant(user, booster, 1);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		inventoryService.activate(user.getId(), booster.getId(), "boost");
		long expBefore = exp(user);

		dailyMissionService.claim(user.getId(), missionId(user, "QUIZ_SCORE_80"), "c");

		assertThat(exp(user)).isEqualTo(expBefore + 40);
		assertThat(jdbc().queryForObject("SELECT bonus_amount FROM experience_logs WHERE source_type = 'MISSION_REWARD'",
				Integer.class)).isZero();
	}

	@Test
	void itemRewardIsGrantedExactlyOnce() {
		catalog();
		ShopItem booster = shopItem(ShopItemType.XP_BOOSTER, 100);
		rewardItem("QUIZ_SCORE_80", booster, 7);

		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long mission = missionId(user, "QUIZ_SCORE_80");

		MissionClaimResultDTO first = dailyMissionService.claim(user.getId(), mission, "item");
		MissionClaimResultDTO replay = dailyMissionService.claim(user.getId(), mission, "item");

		assertThat(first.rewardGranted())
				.isEqualTo(new MissionClaimResultDTO.Granted(20, 40, booster.getCode(), booster.getName(), 1, 0));
		assertThat(replay.rewardGranted()).isEqualTo(first.rewardGranted());
		// The learner reads the item's name, never its code (shop.md §11.3).
		assertThat(dailyMissionService.getToday(user.getId()).missions().stream()
				.filter(m -> m.id().equals(mission)).findFirst().orElseThrow().reward().itemName())
				.isEqualTo(booster.getName());
		assertThat(userItemRepository.findByUserIdAndItemId(user.getId(), booster.getId()).orElseThrow().getQuantity())
				.isEqualTo(1);
	}

	@Test
	void itemRewardFallsBackToCoinWhenTheItemCannotBeGranted() {
		catalog();
		ShopItem frame = shopItem(ShopItemType.AVATAR_FRAME, 50);
		rewardItem("QUIZ_SCORE_80", frame, 7);
		User user = newUser(0);
		grant(user, frame, 1);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long mission = missionId(user, "QUIZ_SCORE_80");

		MissionClaimResultDTO first = dailyMissionService.claim(user.getId(), mission, "fb");
		MissionClaimResultDTO replay = dailyMissionService.claim(user.getId(), mission, "fb");

		assertThat(first.rewardGranted())
				.isEqualTo(new MissionClaimResultDTO.Granted(20, 40, frame.getCode(), frame.getName(), 0, 7));
		assertThat(first.coinBalance()).isEqualTo(27);
		assertThat(replay.replayed()).isTrue();
		assertThat(replay.rewardGranted()).isEqualTo(first.rewardGranted());
		assertThat(coin(user)).isEqualTo(27);
		assertThat(jdbc().queryForList("SELECT event_key FROM coin_transactions WHERE user_id = ? ORDER BY id",
				String.class, user.getId()))
				.containsExactly("MISSION_REWARD:" + mission, "MISSION_REWARD:" + mission + ":ITEM:FALLBACK");
		assertThat(jdbc().queryForMap("SELECT granted_item_quantity, fallback_coin FROM user_daily_mission_claims"))
				.containsEntry("granted_item_quantity", 0).containsEntry("fallback_coin", 7L);
		assertThat(userItemRepository.findByUserIdAndItemId(user.getId(), frame.getId()).orElseThrow().getQuantity())
				.isEqualTo(1);
	}

	/** The constraint retry (§4.4) re-runs the whole claim in a NEW transaction: the first attempt's grant is gone. */
	@Test
	void constraintViolationIsRetriedOnceInAFreshTransactionAndPaysOnce() throws Throwable {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long mission = missionId(user, "QUIZ_SCORE_80");
		long expBefore = exp(user);
		Answer<?> real = Mockito.mockingDetails(claimRepository).getMockCreationSettings().getDefaultAnswer();
		List<Object> transactions = new ArrayList<>();
		List<String> seenByAttempt = new ArrayList<>();
		AtomicInteger attempts = new AtomicInteger();
		doAnswer(inv -> {
			// Spring-level identity of the running transaction, and what that transaction sees (it shares the connection).
			transactions.add(TransactionSynchronizationManager.getResource(entityManagerFactory));
			seenByAttempt.add(jdbc().queryForObject("SELECT CONCAT(u.coin, '/', (SELECT COUNT(*) FROM coin_transactions c "
					+ "WHERE c.user_id = u.id)) FROM users u WHERE u.id = ?", String.class, user.getId()));
			if (attempts.getAndIncrement() == 0) {
				throw new DataIntegrityViolationException("injected: Duplicate entry for uq_mission_claims_key");
			}
			return real.answer(inv);
		}).when(claimRepository).save(any());

		MissionClaimResultDTO result = dailyMissionService.claim(user.getId(), mission, "retry");

		assertThat(attempts.get()).isEqualTo(2);
		assertThat(transactions).hasSize(2).doesNotContainNull();
		assertThat(transactions.get(0)).isNotSameAs(transactions.get(1));
		// Attempt 2 starts from the rolled-back state: had attempt 1 survived it would see 40 coin / 2 rows (or hit
		// UNIQUE event_key on MISSION_REWARD:{id}).
		assertThat(seenByAttempt).containsExactly("20/1", "20/1");
		assertThat(result.replayed()).isFalse();
		assertThat(result.coinBalance()).isEqualTo(20);
		assertThat(coin(user)).isEqualTo(20);
		assertThat(exp(user)).isEqualTo(expBefore + 40);
		assertThat(count("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", user.getId())).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM user_daily_mission_claims")).isEqualTo(1);
	}

	@Test
	void aSecondConstraintViolationPropagatesAndLeavesNothingBehind() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		Long mission = missionId(user, "QUIZ_SCORE_80");
		long expBefore = exp(user);
		doThrow(new DataIntegrityViolationException("injected twice")).when(claimRepository).save(any());

		assertThatThrownBy(() -> dailyMissionService.claim(user.getId(), mission, "twice"))
				.isInstanceOf(DataIntegrityViolationException.class);

		verify(claimRepository, times(2)).save(any());
		assertThat(status(user, "QUIZ_SCORE_80")).isEqualTo("COMPLETED");
		assertThat(coin(user)).isZero();
		assertThat(exp(user)).isEqualTo(expBefore);
		assertThat(count("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", user.getId())).isZero();
	}

	@Test
	void templateEditsNeverChangeAnAssignedSnapshot() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		dailyMissionService.getToday(user.getId());
		MissionTemplate score80 = templateRepository.findByCode("QUIZ_SCORE_80").orElseThrow();
		score80.setRewardCoin(999);
		score80.setTargetValue(3);
		score80.setName("Edited");
		templateRepository.save(score80);

		completeQuiz(user, topic, 5, 5);
		DailyMissionsDTO.Mission shown = dailyMissionService.getToday(user.getId()).missions().stream()
				.filter(m -> m.code().equals("QUIZ_SCORE_80")).findFirst().orElseThrow();
		MissionClaimResultDTO claimed = dailyMissionService.claim(user.getId(), shown.id(), "snap");

		assertThat(shown.name()).isEqualTo("Trí Nhớ Siêu Phàm");
		assertThat(shown.targetValue()).isEqualTo(1);
		assertThat(shown.reward().coin()).isEqualTo(20);
		assertThat(claimed.rewardGranted().coin()).isEqualTo(20);
	}

	@Test
	void claimingAllFiveGivesNoExtraReward() {
		catalog();
		User user = newUser(0);
		Topic topic = userTopicWithWords(user, 6);
		completeQuiz(user, topic, 5, 5);
		completeQuiz(user, topic, 5, 5);

		long coin = 0;
		for (DailyMissionsDTO.Mission m : dailyMissionService.getToday(user.getId()).missions()) {
			assertThat(m.status()).isEqualTo(DailyMissionStatus.COMPLETED);
			coin += dailyMissionService.claim(user.getId(), m.id(), "all-" + m.slot()).rewardGranted().coin();
		}

		assertThat(coin).isEqualTo(85);
		assertThat(coin(user)).isEqualTo(85);
		assertThat(count("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", user.getId())).isEqualTo(5);
	}

	// --- Catalog seed (§7.7) ---------------------------------------------------------------------

	@Test
	void startupCheckSkipsAnInactiveCatalogButNeverALiveOne() {
		MissionCatalogSeeder seeder = seeder();
		List<MissionTemplate> nothingToSeed = List.of();

		// Not activated: nothing active ⇒ startup continues.
		seeder.seed(nothingToSeed);
		templateRepository.save(inactive(template("IT_OFF", MissionCategory.QUIZ, MissionTriggerEvent.QUIZ_COMPLETED)));
		seeder.seed(nothingToSeed);

		// Live but misconfigured (e.g. rows inserted by SQL) ⇒ fail fast.
		templateRepository.save(template("IT_LIVE_1", MissionCategory.QUIZ, MissionTriggerEvent.QUIZ_COMPLETED));
		templateRepository.save(template("IT_LIVE_2", MissionCategory.RETENTION,
				MissionTriggerEvent.LEARNING_ACTIVITY_RECORDED));
		assertThatThrownBy(() -> seeder.seed(nothingToSeed))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("invariant");

		// Live and valid ⇒ startup continues.
		for (String code : List.of("IT_LIVE_3", "IT_LIVE_4", "IT_LIVE_5")) {
			templateRepository.save(template(code, MissionCategory.QUIZ, MissionTriggerEvent.QUIZ_COMPLETED));
		}
		seeder.seed(nothingToSeed);
	}

	@Test
	void seedIsIdempotentNeverOverwritesAndFailsFastOnABrokenCatalog() {
		MissionCatalogSeeder seeder = seeder();
		jdbc().update("DELETE FROM admin_audit_logs");
		seeder.seed(MissionCatalogSeeder.mvpCatalog());
		// BE-AUD-09: one "Hệ thống" row per seeded template.
		assertThat(count("SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'MISSION_TEMPLATE_CREATED'"
				+ " AND actor_id IS NULL")).isEqualTo(12);
		MissionTemplate edited = templateRepository.findByCode("KEEP_DAILY_STREAK").orElseThrow();
		edited.setWeight(9);
		templateRepository.save(edited);
		seeder.seed(MissionCatalogSeeder.mvpCatalog());

		assertThat(templateRepository.count()).isEqualTo(12);
		assertThat(templateRepository.findByCode("KEEP_DAILY_STREAK").orElseThrow().getWeight()).isEqualTo(9);

		deactivate("ADD_5_WORDS", "ADD_3_WORDS");
		assertThatThrownBy(() -> seeder.seed(MissionCatalogSeeder.mvpCatalog()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("invariant");
	}
	// --- Fixtures --------------------------------------------------------------------------------

	/**
	 * The §5.1 catalog as these tests were written for it: the PD-DM-07 templates (new triggers, which service-level
	 * calls do not filter) are off; tests that need one turn it on.
	 */
	private void catalog() {
		templateRepository.saveAll(MissionCatalogSeeder.mvpCatalog());
		deactivate("SCAN_SAVE_3_WORDS", "SCAN_SAVE_5_WORDS", "STUDY_5_CARDS", "STUDY_10_CARDS");
	}

	private static MissionTemplate template(String code, MissionCategory category, MissionTriggerEvent trigger) {
		return MissionTemplate.builder().code(code).name(code).description(code).category(category)
				.triggerEvent(trigger).targetValue(1).rewardCoin(1).rewardXp(1).active(true).build();
	}

	private void rewardItem(String code, ShopItem item, int fallbackCoin) {
		MissionTemplate template = templateRepository.findByCode(code).orElseThrow();
		template.setRewardItemCode(item.getCode());
		template.setRewardItemFallbackCoin(fallbackCoin);
		templateRepository.save(template);
	}

	/**
	 * Blocks until another connection has been stuck for >= 1 s in the users row-lock query (SELECT ... FOR UPDATE),
	 * i.e. it is waiting for the lock the held quiz transaction owns.
	 */
	private void awaitUsersLockWait() throws InterruptedException {
		for (int i = 0; i < 300; i++) {
			if (count("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE COMMAND = 'Query' AND TIME >= 1 "
					+ "AND INFO LIKE '%from users%for update%'") > 0) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("no connection ever waited for the users row lock: "
				+ jdbc().queryForList("SELECT ID, COMMAND, TIME, STATE, LEFT(INFO, 120) AS info FROM "
						+ "information_schema.PROCESSLIST WHERE COMMAND <> 'Sleep'"));
	}
	private static MissionTemplate inactive(MissionTemplate template) {
		template.setActive(false);
		return template;
	}

	private void deactivate(String... codes) {
		for (String code : codes) {
			MissionTemplate t = templateRepository.findByCode(code).orElseThrow();
			t.setActive(false);
			templateRepository.save(t);
		}
	}

	private User newUser(long coin) {
		return userRepository.save(User.builder()
				.email("dm-it-" + UUID.randomUUID() + "@test.local")
				.password("x".repeat(60))
				.firstName("DM")
				.activated(true)
				.coin(coin)
				.build());
	}

	private static void authenticate(User user) {
		authenticate(user, List.of());
	}

	private static void authenticateAdmin(User user) {
		authenticate(user, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	private static void authenticate(User user,
			List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities) {
		Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(user.getEmail())
				.claim("userId", user.getId()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
	}

	private Topic userTopic(User user) {
		return topic(collectionRepository.save(Collection.builder().name("mine").type(CollectionType.USER)
				.owner(user).build()));
	}

	private Topic topic(Collection collection) {
		Schema schema = schemaRepository.findByCode("DEFAULT_ENGLISH").orElseThrow();
		return topicRepository.save(Topic.builder()
				.collection(collection)
				.name("DM topic")
				.schema(schema)
				.activeTemplate(templateLayoutRepository.findBySchemaIdAndCode(schema.getId(), "STANDARD").orElseThrow())
				.build());
	}

	/** Words added through the real add-item path (so they also feed ADD_5_WORDS). */
	private Topic userTopicWithWords(User user, int size) {
		Topic topic = userTopic(user);
		authenticate(user);
		for (int i = 1; i <= size; i++) {
			topicService.addTopicItem(topic.getId(), word("word" + i, "nghĩa " + i));
		}
		return topic;
	}

	private String scan(User user, String status, String detections) {
		String id = UUID.randomUUID().toString();
		jdbc().update("INSERT INTO scan_requests (id, user_id, status, result_json, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, NOW(6), NOW(6))", id, user.getId(), status,
				detections == null ? null : "{\"imageWidth\":1,\"imageHeight\":1,\"detections\":" + detections + "}");
		return id;
	}

	private static String detection(String label, String headwordJson) {
		return "{\"label\":\"" + label + "\",\"headword\":" + headwordJson + ",\"score\":1,\"source\":\"IT\","
				+ "\"reliability\":\"HIGH\",\"box\":{\"x1\":0,\"y1\":0,\"x2\":1,\"y2\":1}}";
	}

	private static CreateTopicItemDTO word(String word, String translation) {
		Map<String, Object> main = new LinkedHashMap<>();
		main.put("word", word);
		if (translation != null) {
			main.put("translation", translation);
		}
		Map<String, Object> groups = new LinkedHashMap<>();
		groups.put("main", main);
		groups.put("meaning", Map.of("meaning", "definition of " + word));
		return new CreateTopicItemDTO(groups);
	}

	private Long startQuiz(User user, Topic topic, int size) {
		authenticate(user);
		return quizService.create(new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, size),
				UUID.randomUUID().toString()).quizId();
	}

	private void answer(Long quizId, int correct) {
		List<QuizQuestion> questions = tx().execute(s -> {
			Quiz quiz = quizRepository.findById(quizId).orElseThrow();
			quiz.getQuestions().forEach(q -> q.getOptions().size());
			return new ArrayList<>(quiz.getQuestions());
		});
		for (int i = 0; i < questions.size(); i++) {
			QuizQuestion q = questions.get(i);
			String key = i < correct ? q.getCorrectOptionKey()
					: q.getOptions().stream().map(o -> o.getOptionKey())
							.filter(k -> !k.equals(q.getCorrectOptionKey())).findFirst().orElseThrow();
			quizService.checkQuestion(q.getId(), new CheckQuestionRequest(key, null), "ans-" + q.getId());
		}
	}

	private Long completeQuiz(User user, Topic topic, int size, int correct) {
		Long quizId = startQuiz(user, topic, size);
		answer(quizId, correct);
		quizService.complete(quizId);
		return quizId;
	}

	private ShopItem shopItem(ShopItemType type, long price) {
		ShopItem.ShopItemBuilder builder = ShopItem.builder()
				.code("IT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
				.type(type).name(type + " item").status(ShopItemStatus.PUBLISHED).purchasable(true).price(price);
		if (type == ShopItemType.XP_BOOSTER) {
			builder.boostDurationMinutes(30).maxQuantity(5);
		} else {
			builder.frameAssetKey("frames/it.png");
		}
		return shopItemRepository.save(builder.build());
	}

	private void grant(User user, ShopItem item, int quantity) {
		userItemRepository.save(UserItem.builder().user(user).item(item).itemType(item.getType()).quantity(quantity)
				.build());
	}

	private Map<String, Integer> progress(User user) {
		Map<String, Integer> result = new HashMap<>();
		jdbc().query("SELECT code, current_progress FROM user_daily_missions WHERE user_id = ? AND mission_date = ?",
				rs -> {
					result.put(rs.getString(1), rs.getInt(2));
				}, user.getId(), MissionTracker.missionDate(Instant.now()));
		return result;
	}

	private String status(User user, String code) {
		return statusOn(user, MissionTracker.missionDate(Instant.now()), code);
	}

	private String statusOn(User user, LocalDate date, String code) {
		return jdbc().queryForObject("SELECT status FROM user_daily_missions WHERE user_id = ? AND mission_date = ? "
				+ "AND code = ?", String.class, user.getId(), date, code);
	}

	private Long missionId(User user, String code) {
		return missionId(user, MissionTracker.missionDate(Instant.now()), code);
	}

	private Long missionId(User user, LocalDate date, String code) {
		return jdbc().queryForObject("SELECT id FROM user_daily_missions WHERE user_id = ? AND mission_date = ? "
				+ "AND code = ?", Long.class, user.getId(), date, code);
	}

	private List<String> sourceKeys(User user, String code) {
		return jdbc().queryForList("SELECT l.source_key FROM user_daily_mission_progress_logs l "
				+ "JOIN user_daily_missions m ON m.id = l.user_daily_mission_id WHERE m.user_id = ? AND m.code = ?",
				String.class, user.getId(), code);
	}

	private long coin(User user) {
		return jdbc().queryForObject("SELECT coin FROM users WHERE id = ?", Long.class, user.getId());
	}

	private long exp(User user) {
		return jdbc().queryForObject("SELECT exp FROM users WHERE id = ?", Long.class, user.getId());
	}

	private double poolInsufficientCount() {
		return meterRegistry.counter(MissionTracker.POOL_INSUFFICIENT_METRIC).count();
	}

	private long count(String sql, Object... args) {
		return jdbc().queryForObject(sql, Long.class, args);
	}

	private static MissionErrorCode codeOf(Runnable action) {
		try {
			action.run();
		} catch (MissionException e) {
			return e.getCode();
		}
		throw new AssertionError("expected a MissionException");
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	private interface Task<T> {
		T run(int index) throws Exception;
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> runConcurrently(int threads, Task<?> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				int index = i;
				futures.add(pool.submit((Callable<T>) () -> {
					start.await();
					try {
						return (T) task.run(index);
					} finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get(60, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

}
