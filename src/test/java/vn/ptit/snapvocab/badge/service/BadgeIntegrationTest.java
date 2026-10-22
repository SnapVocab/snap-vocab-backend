package vn.ptit.snapvocab.badge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.dto.BadgesDTO;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.config.DataInitializer;
import vn.ptit.snapvocab.mission.service.MissionTracker;
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
import vn.ptit.snapvocab.quiz.service.QuizGenerator;
import vn.ptit.snapvocab.quiz.service.QuizService;
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

/** Badges (badges.md §6, §8, AC §12) through real quiz / add-item producers against LOCAL MySQL. */
@LocalMySqlJpaTest
@Import({ DataInitializer.class, SemanticBindingMigration.class, QuizService.class, QuizContentResolver.class,
		QuizGenerator.class, XpService.class, TopicService.class, MissionTracker.class, ProgressService.class,
		StreakFreezeService.class, ProgressEventListener.class, BadgeService.class, BadgeIntegrationTest.Beans.class })
class BadgeIntegrationTest {

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

	// addTopicItem uses none of the mappers.
	@MockitoBean
	TopicMapper topicMapper;
	@MockitoBean
	TopicSchemaMapper topicSchemaMapper;
	@MockitoBean
	SchemaMapper schemaMapper;

	@Autowired
	BadgeService badgeService;
	@Autowired
	BadgeRepository badgeRepository;
	@Autowired
	QuizService quizService;
	@Autowired
	TopicService topicService;
	@Autowired
	UserRepository userRepository;
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

	@BeforeEach
	void seedCatalog() {
		new BadgeCatalogSeeder(badgeRepository).seed(BadgeCatalogSeeder.mvpCatalog());
	}

	@AfterEach
	void cleanUp() {
		SecurityContextHolder.clearContext();
		JdbcTemplate jdbc = jdbc();
		for (String table : List.of("user_badges", "user_badge_word_credits", "badges", "user_word_credits",
				"user_daily_mission_progress_logs", "user_daily_missions", "learning_events", "quiz_matching_attempts",
				"quiz_question_options", "quiz_matching_tiles", "quiz_questions", "quizzes",
				"topic_item_attribute_values", "topic_item_attribute_groups", "topic_items", "topics", "collections",
				"experience_logs")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'bdg-it-%'");
	}

	// --- Granting (§6.1, AC §12.1) ---------------------------------------------------------------

	@Test
	void getNeverGrantsAndSyncGrantsOnceWithUnseenFlags() {
		User user = newUser();
		Topic topic = topicWithWords(user, 5);
		completeQuiz(user, topic, 5, 5);

		BadgesDTO before = badgeService.getBadges(user.getId());
		assertThat(item(before, "QUIZ_1").earned()).isFalse();
		assertThat(item(before, "QUIZ_1").currentValue()).isEqualTo(1);
		assertThat(userBadges(user)).isZero();

		BadgesDTO synced = badgeService.sync(user.getId());
		assertThat(synced.earnedCount()).isEqualTo(2);
		assertThat(synced.unseenCount()).isEqualTo(2);
		assertThat(synced.totalCount()).isEqualTo(12);
		assertThat(item(synced, "QUIZ_1").earned()).isTrue();
		assertThat(item(synced, "QUIZ_1").seen()).isFalse();
		assertThat(item(synced, "QUIZ_1").earnedAt()).isNotNull();
		assertThat(item(synced, "PERFECT_1").earned()).isTrue();
		assertThat(item(synced, "QUIZ_10").seen()).isNull();
		assertThat(item(synced, "WORDS_10").currentValue()).isEqualTo(5);

		badgeService.sync(user.getId());
		assertThat(userBadges(user)).isEqualTo(2);
		assertThat(badgeService.getBadges(user.getId()).earnedCount()).isEqualTo(2);
	}

	@Test
	void imperfectQuizCountsAsCompletedButNotPerfect() {
		User user = newUser();
		completeQuiz(user, topicWithWords(user, 5), 5, 4);

		BadgesDTO synced = badgeService.sync(user.getId());

		assertThat(item(synced, "QUIZ_1").earned()).isTrue();
		assertThat(item(synced, "PERFECT_1").earned()).isFalse();
		assertThat(item(synced, "PERFECT_1").currentValue()).isZero();
	}

	@Test
	void streakBadgesUseTheLongestStreakSoALostStreakStillCounts() {
		User user = newUser();
		user.setLongestStreakDays(30);
		user.setStreakDays(0);
		userRepository.save(user);

		BadgesDTO synced = badgeService.sync(user.getId());

		assertThat(item(synced, "STREAK_7").earned()).isTrue();
		assertThat(item(synced, "STREAK_30").earned()).isTrue();
		assertThat(item(synced, "STREAK_100").earned()).isFalse();
	}

	@Test
	void deactivatedBadgeIsNotGrantedButEarnersStillSeeIt() {
		User earner = newUser();
		completeQuiz(earner, topicWithWords(earner, 5), 5, 5);
		badgeService.sync(earner.getId());
		setActive("QUIZ_1", false);
		setActive("PERFECT_1", false);

		User latecomer = newUser();
		completeQuiz(latecomer, topicWithWords(latecomer, 5), 5, 5);
		BadgesDTO late = badgeService.sync(latecomer.getId());
		BadgesDTO early = badgeService.getBadges(earner.getId());

		assertThat(late.badges()).extracting(BadgesDTO.Item::code).doesNotContain("QUIZ_1", "PERFECT_1");
		assertThat(late.totalCount()).isEqualTo(10);
		assertThat(early.badges()).extracting(BadgesDTO.Item::code).contains("QUIZ_1", "PERFECT_1");
		assertThat(early.totalCount()).isEqualTo(12);
	}

	@Test
	void parallelSyncsGrantEachBadgeOnce() throws Exception {
		User user = newUser();
		completeQuiz(user, topicWithWords(user, 5), 5, 5);

		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<BadgesDTO>> results = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Callable<BadgesDTO> call = () -> badgeService.sync(user.getId());
				results.add(pool.submit(call));
			}
			for (Future<BadgesDTO> result : results) {
				assertThat(result.get().earnedCount()).isEqualTo(2);
			}
		} finally {
			pool.shutdown();
		}
		assertThat(userBadges(user)).isEqualTo(2);
	}

	// --- Distinct words (§6.2, AC §12.2) ---------------------------------------------------------

	@Test
	void wordCreditsCountEachNormalizedQuizReadyWordOnce() {
		User user = newUser();
		Topic topic = userTopic(user);
		Topic other = userTopic(user);
		authenticate(user);

		topicService.addTopicItem(topic.getId(), word("Cat", "mèo"));
		topicService.addTopicItem(other.getId(), word("  cat ", "con mèo"));
		topicService.addTopicItem(topic.getId(), word("dog", "chó"));
		topicService.addTopicItem(topic.getId(), word("bird", null));

		assertThat(count("SELECT COUNT(*) FROM user_badge_word_credits WHERE user_id = ?", user.getId())).isEqualTo(2);
		assertThat(item(badgeService.getBadges(user.getId()), "WORDS_10").currentValue()).isEqualTo(2);
	}

	@Test
	void wordsInASystemCollectionNeverCount() {
		User user = newUser();
		Topic system = topic(collectionRepository.save(Collection.builder().name("sys").type(CollectionType.SYSTEM)
				.build()));
		authenticate(user, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));

		topicService.addTopicItem(system.getId(), word("system", "hệ thống"));

		assertThat(count("SELECT COUNT(*) FROM user_badge_word_credits")).isZero();
	}

	// --- Seen (§8.3, AC §12.3) -------------------------------------------------------------------

	@Test
	void markSeenIsIdempotentAndIgnoresForeignOrUnearnedIds() {
		User user = newUser();
		completeQuiz(user, topicWithWords(user, 5), 5, 5);
		BadgesDTO synced = badgeService.sync(user.getId());
		Long quiz1 = item(synced, "QUIZ_1").id();
		Long quiz10 = item(synced, "QUIZ_10").id();

		assertThat(badgeService.markSeen(user.getId(), List.of(quiz1, quiz10, 999_999L)).unseenCount()).isEqualTo(1);
		assertThat(badgeService.markSeen(user.getId(), List.of(quiz1)).unseenCount()).isEqualTo(1);
		BadgesDTO after = badgeService.getBadges(user.getId());
		assertThat(item(after, "QUIZ_1").seen()).isTrue();
		assertThat(item(after, "PERFECT_1").seen()).isFalse();
		assertThat(item(after, "QUIZ_10").seen()).isNull();
	}

	@Test
	void markSeenRejectsEmptyOversizedOrInvalidIds() {
		User user = newUser();
		List<Long> tooMany = new ArrayList<>();
		for (long i = 1; i <= 51; i++) {
			tooMany.add(i);
		}
		List<Long> withNull = new ArrayList<>();
		withNull.add(null);

		for (List<Long> ids : List.of(List.<Long>of(), tooMany, List.of(0L), withNull)) {
			assertThatThrownBy(() -> badgeService.markSeen(user.getId(), ids))
					.isInstanceOf(BadRequestAlertException.class).hasMessage("VALIDATION_ERROR");
		}
		assertThatThrownBy(() -> badgeService.markSeen(user.getId(), null))
				.isInstanceOf(BadRequestAlertException.class);
	}

	// --- Helpers ---------------------------------------------------------------------------------

	private static BadgesDTO.Item item(BadgesDTO dto, String code) {
		return dto.badges().stream().filter(i -> i.code().equals(code)).findFirst().orElseThrow();
	}

	private long userBadges(User user) {
		return count("SELECT COUNT(*) FROM user_badges WHERE user_id = ?", user.getId());
	}

	private void setActive(String code, boolean active) {
		Badge badge = badgeRepository.findByCode(code).orElseThrow();
		badge.setActive(active);
		badgeRepository.save(badge);
	}

	private User newUser() {
		return userRepository.save(User.builder()
				.email("bdg-it-" + UUID.randomUUID() + "@test.local")
				.password("x".repeat(60))
				.firstName("BDG")
				.activated(true)
				.coin(0L)
				.build());
	}

	private static void authenticate(User user) {
		authenticate(user, List.of());
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
				.name("BDG topic")
				.schema(schema)
				.activeTemplate(templateLayoutRepository.findBySchemaIdAndCode(schema.getId(), "STANDARD").orElseThrow())
				.build());
	}

	private Topic topicWithWords(User user, int size) {
		Topic topic = userTopic(user);
		authenticate(user);
		for (int i = 1; i <= size; i++) {
			topicService.addTopicItem(topic.getId(), word("word" + i, "nghĩa " + i));
		}
		return topic;
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

	private void completeQuiz(User user, Topic topic, int size, int correct) {
		authenticate(user);
		Long quizId = quizService.create(new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, size),
				UUID.randomUUID().toString()).quizId();
		List<QuizQuestion> questions = new TransactionTemplate(transactionManager).execute(s -> {
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
		quizService.complete(quizId);
	}

	private long count(String sql, Object... args) {
		return jdbc().queryForObject(sql, Long.class, args);
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}
}
