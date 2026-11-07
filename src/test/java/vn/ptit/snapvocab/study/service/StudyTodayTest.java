package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.openspacedrepetition.Rating;
import io.minio.MinioClient;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.study.domain.dto.StudyCardDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyCollectionDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyTodayDTO;
import vn.ptit.snapvocab.study.exception.StudyQuotaExceededException;
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
 * Daily new/review limits are independent and shared by a collection's topic tree.
 * Home counts the available daily plan; session size only controls the next batch.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
class StudyTodayTest {

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;

	@Autowired
	StudyService studyService;
    @Autowired
    CollectionStudySettingsService collectionSettings;
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
	EntityManager entityManager;
	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void noCaching() {
		// A fresh cache per lookup keeps the integration fixture independent from cached topic data.
		when(cacheManager.getCache(anyString())).thenAnswer(call -> new ConcurrentMapCache(call.getArgument(0)));
	}

	@Test
	void newWordsAreLimitedPerCollectionWhileIntradayLearningIsNotCapped() {
		User user = learner();
		Collection own = collection(user, "own");
		Collection started = collection(null, "started system");
		Collection untouched = collection(null, "untouched system");
        collectionSettings.update(user.getId(), own.getId(), new vn.ptit.snapvocab.study.domain.dto.UpdateCollectionStudySettingsRequest(1, 200));
        collectionSettings.update(user.getId(), started.getId(), new vn.ptit.snapvocab.study.domain.dto.UpdateCollectionStudySettingsRequest(1, 200));
		TopicItem own1 = item(own);
		item(own);
		TopicItem sys1 = item(started);
		TopicItem sys2 = item(started);
		item(untouched);

		studyService.submitTodayRating(user.getId(), own1.getId(), Rating.GOOD);
		studyService.submitCollectionRating(user.getId(), started.getId(), sys1.getId(), Rating.GOOD);
		// A second new card in the same capped collection is refused; re-rating a started card is not.
		assertThatThrownBy(() -> studyService.submitCollectionRating(user.getId(), started.getId(), sys2.getId(),
				Rating.GOOD)).isInstanceOf(StudyQuotaExceededException.class);
		studyService.submitTodayRating(user.getId(), own1.getId(), Rating.EASY);

		makeEverythingDue(user);
		StudyTodayDTO today = studyService.getToday(user.getId());
		assertThat(today.dueCount()).isEqualTo(2);
		assertThat(today.newCount()).isZero();
		assertThat(today.sessionCount()).isEqualTo(2);
		assertThat(today.studiedToday()).isEqualTo(2);
		assertThat(today.totalWords()).as("own 2 + started system 2; untouched system excluded").isEqualTo(4);

		assertThat(studyService.getTodayQueue(user.getId()).cards()).extracting(StudyCardDTO::collectionName)
				.containsExactlyInAnyOrder("own", "started system");
		assertThat(studyService.getLibrary(user.getId())).extracting(StudyCollectionDTO::name)
				.containsExactlyInAnyOrder("own", "started system");
	}

	@Test
	void backlogAndBreakDoNotOverrideIndependentCollectionLimits() {
		User user = learner();
		Collection own = collection(user, "own");
		for (int i = 0; i < 4; i++) {
			studyService.submitTodayRating(user.getId(), item(own).getId(), Rating.GOOD);
		}
		item(own);
		makeEverythingDue(user);

		StudyTodayDTO backlog = studyService.getToday(user.getId());
		assertThat(backlog.backlog()).isFalse();
		assertThat(backlog.newCount()).isEqualTo(1);
		assertThat(backlog.sessionCount()).isEqualTo(5);

		entityManager.flush();
		jdbc.update("DELETE FROM study_daily_cards WHERE user_id = ?", user.getId());
		jdbc.update("UPDATE users SET last_studied_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 DAY) WHERE id = ?",
				user.getId());
		entityManager.clear();

		StudyTodayDTO returning = studyService.getToday(user.getId());
		assertThat(returning.returning()).isFalse();
		assertThat(returning.backlog()).isFalse();
		assertThat(returning.newCount()).isEqualTo(1);
		assertThat(returning.sessionCount()).isEqualTo(5);
		assertThat(studyService.getTodayQueue(user.getId()).limit()).isEqualTo(20);
	}

	private User learner() {
		User user = userRepository.save(User.builder()
				.email("study-today-" + UUID.randomUUID() + "@snapvocab.local")
				.password("a".repeat(60))
				.firstName("Learner")
				.activated(true)
				.build());
		return user;
	}

	private Collection collection(User owner, String name) {
		return collectionRepository.save(Collection.builder().name(name)
				.type(owner == null ? CollectionType.SYSTEM : CollectionType.USER).owner(owner).build());
	}

	private TopicItem item(Collection collection) {
		Topic topic = topicRepository.findByCollectionId(collection.getId()).stream().findFirst()
				.orElseGet(() -> topicRepository.save(Topic.builder().collection(collection).name("topic")
						.schema(schemaRepository.findByCode("DEFAULT_ENGLISH").orElseThrow()).build()));
		return topicItemRepository.save(TopicItem.builder().topic(topic).build());
	}

	private void makeEverythingDue(User user) {
		entityManager.flush();
		jdbc.update("UPDATE fsrs_records SET due = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY) WHERE user_id = ?",
				user.getId());
		entityManager.clear();
	}
}
