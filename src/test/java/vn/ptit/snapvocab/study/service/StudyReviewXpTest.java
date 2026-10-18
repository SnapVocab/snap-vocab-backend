package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.openspacedrepetition.Rating;
import io.minio.MinioClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
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

/** Review XP (PO 2026-10-18): 2 XP for a card's first review of the study day, nothing for re-rating it. */
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

		studyService.submitRating(user.getId(), topic.getId(), first.getId(), Rating.AGAIN);
		studyService.submitRating(user.getId(), topic.getId(), first.getId(), Rating.GOOD);
		studyService.submitRating(user.getId(), topic.getId(), second.getId(), Rating.GOOD);

		assertThat(userRepository.findById(user.getId()).orElseThrow().getExp()).isEqualTo(2L * StudyService.XP_PER_REVIEW);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM experience_logs WHERE user_id = ? AND source_type = 'FLASHCARD_REVIEW'"
				+ " AND base_amount = ?", Integer.class, user.getId(), StudyService.XP_PER_REVIEW)).isEqualTo(2);
	}
}
