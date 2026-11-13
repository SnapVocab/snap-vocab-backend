package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.openspacedrepetition.Rating;
import io.minio.MinioClient;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.DailyMissionService;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

/**
 * Review XP (PO 2026-10-18): 2 XP for a card's first review of the study day, nothing for re-rating it.
 * The same first review is also a learning activity: streak and the KEEP_DAILY_STREAK mission, and it advances the
 * slot 3 STUDY_5_CARDS mission (PD-DM-07).
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
@Transactional
class StudyReviewXpTest {

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;

	@Autowired
	StudyService studyService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	CollectionRepository collectionRepository;
	@Autowired
	TopicRepository topicRepository;
	@Autowired
	TopicItemRepository topicItemRepository;
	@Autowired
	SchemaRepository schemaRepository;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	EntityManager entityManager;
	@Autowired
	DailyMissionService dailyMissionService;

	@BeforeEach
	void noCaching() {
		// StudyService.getToday reads @Cacheable settings (HAS_STUDY_CARDS_FOR_TARGET); same stub as StudyTodayTest.
		when(cacheManager.getCache(anyString())).thenAnswer(call -> new ConcurrentMapCache(call.getArgument(0)));
		// The test's mock request stands for the app: a current build announces every trigger (PD-DM-13).
		((MockHttpServletRequest) ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
				.getRequest()).addHeader(MissionTracker.TRIGGERS_HEADER, Arrays.stream(MissionTriggerEvent.values())
						.map(Enum::name).collect(Collectors.joining(",")));
	}

	@Test
	void firstReviewOfEachCardPerDayEarnsXp() {
		User user = userRepository.save(User.builder()
				.email("study-xp-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Learner")
				.activated(true)
				.build());
		Collection collection = collectionRepository.save(Collection.builder().name("sys").type(CollectionType.SYSTEM).build());
		Topic topic = topicRepository.save(Topic.builder()
				.collection(collection)
				.name("topic")
				.schema(schemaRepository.findByCode("DEFAULT_ENGLISH").orElseThrow())
				.build());
		TopicItem first = topicItemRepository.save(TopicItem.builder().topic(topic).build());
		TopicItem second = topicItemRepository.save(TopicItem.builder().topic(topic).build());
		// Seeded off until the app that renders it ships (daily_mission.md §5.1).
		jdbc.update("UPDATE mission_templates SET is_active = 1 WHERE code = 'STUDY_5_CARDS'");

		studyService.submitRating(user.getId(), topic.getId(), first.getId(), Rating.AGAIN);
		studyService.submitRating(user.getId(), topic.getId(), first.getId(), Rating.GOOD);
		studyService.submitRating(user.getId(), topic.getId(), second.getId(), Rating.GOOD);

		assertThat(userRepository.findById(user.getId()).orElseThrow().getExp()).isEqualTo(2L * StudyService.XP_PER_REVIEW);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experience_logs WHERE user_id = ? AND source_type = 'FLASHCARD_REVIEW'"
				+ " AND base_amount = ?", Integer.class, user.getId(), StudyService.XP_PER_REVIEW)).isEqualTo(2);

		// Every accepted rating is a learning activity (master 2026-10-31); only XP stays once per card per day.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_events WHERE user_id = ? AND event_type = 'SRS_REVIEW_COMPLETED'",
				Integer.class, user.getId())).isEqualTo(3);
		assertThat(userRepository.findById(user.getId()).orElseThrow().getStreakDays()).isEqualTo(1);
		// Coin 0 and no booster ⇒ slot 5 is KEEP_DAILY_STREAK (daily_mission.md §5.1).
		assertThat(jdbc.queryForObject("SELECT status FROM user_daily_missions WHERE user_id = ? AND code = 'KEEP_DAILY_STREAK'",
				String.class, user.getId())).isEqualTo("COMPLETED");
		// Distinct cards per day, like XP: re-rating the first card does not count (daily_mission.md §5.1). Slot 3 is its own slot.
		entityManager.flush();
		assertThat(jdbc.queryForMap("SELECT slot, current_progress FROM user_daily_missions WHERE user_id = ?"
				+ " AND code = 'STUDY_5_CARDS'", user.getId())).containsEntry("slot", 3).containsEntry("current_progress", 2);
	}

	@Test
	void studyTenCardsGoesOnlyToALearnerWithTenCardsToday() { // PD-DM-12, HAS_STUDY_CARDS_FOR_TARGET (daily_mission.md §4.3)
		jdbc.update("UPDATE mission_templates SET is_active = (code = 'STUDY_10_CARDS')"
				+ " WHERE code IN ('STUDY_5_CARDS', 'STUDY_10_CARDS')");
		User ready = learnerWithOwnWords(10);
		User shortOfCards = learnerWithOwnWords(9);

		assertThat(dailyMissionService.getToday(ready.getId()).missions().get(2).code()).isEqualTo("STUDY_10_CARDS");
		// Not eligible and no other SRS template: slot 3 falls back to a core template.
		assertThat(dailyMissionService.getToday(shortOfCards.getId()).missions())
				.noneMatch(m -> m.code().equals("STUDY_10_CARDS"));
	}

	private User learnerWithOwnWords(int words) {
		User user = userRepository.save(User.builder()
				.email("study-xp-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Learner")
				.activated(true)
				.build());
		Collection mine = collectionRepository.save(Collection.builder().name("mine").type(CollectionType.USER)
				.owner(user).build());
		Topic topic = topicRepository.save(Topic.builder()
				.collection(mine)
				.name("topic")
				.schema(schemaRepository.findByCode("DEFAULT_ENGLISH").orElseThrow())
				.build());
		for (int i = 0; i < words; i++) {
			topicItemRepository.save(TopicItem.builder().topic(topic).build());
		}
		return user;
	}
}
