package vn.ptit.snapvocab.vocab.service;

import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.dto.topic.DefaultTopicDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

/**
 * The learner's default topic (buss_mainflow.md BF-07: "Topic gần nhất → Topic mặc định → Đổi Topic").
 * <p>
 * Created on first need rather than at sign-up, so accounts made before this existed get one too. It is an ordinary
 * USER topic: the learner can rename or delete it, and a deleted one is replaced on the next call.
 */
@Service
@RequiredArgsConstructor
public class DefaultTopicService {

	/** Accounts that got their default topic before sign-up created "Bộ từ của tôi" keep using this collection. */
	static final String LEGACY_COLLECTION_NAME = "My vocabulary";
	static final String TOPIC_NAME = "New words";
	static final String TOPIC_TRANSLATION = "Từ mới";
	static final String DEFAULT_SCHEMA_CODE = "DEFAULT_ENGLISH";

	private final Logger LOG = LoggerFactory.getLogger(DefaultTopicService.class);

	private final UserRepository userRepository;
	private final CollectionRepository collectionRepository;
	private final TopicRepository topicRepository;
	private final SchemaRepository schemaRepository;
	private final TemplateRepository templateRepository;
	private final CollectionService collectionService;

	@Transactional
	@CacheEvict(cacheNames = "topic-tree", allEntries = true)
	public DefaultTopicDTO getOrCreate(Long userId) {
		// Row lock first: two saves racing on a fresh account must not create two default topics.
		User user = Economy.lockUser(userRepository, userId);

		Optional<Topic> existing = Optional.ofNullable(user.getDefaultTopicId())
				.flatMap(topicRepository::findById)
				.filter(topic -> ownsUserTopic(topic, userId));
		if (existing.isPresent()) {
			return toDto(existing.get());
		}

		// The collection sign-up creates (CollectionService.ensureDefaultUserCollection), so the default topic does
		// not bring a second "my words" collection.
		Collection collection = findUserCollection(CollectionService.DEFAULT_USER_COLLECTION_NAME, userId)
				.or(() -> findUserCollection(LEGACY_COLLECTION_NAME, userId))
				.orElseGet(() -> {
					Collection created = collectionRepository.save(Collection.builder()
							.name(CollectionService.DEFAULT_USER_COLLECTION_NAME)
							.translation(CollectionService.DEFAULT_USER_COLLECTION_TRANSLATION)
							.type(CollectionType.USER)
							.owner(user)
							.build());
					collectionService.evictUserCollectionsCache(userId);
					return created;
				});

		Schema schema = schemaRepository.findByCode(DEFAULT_SCHEMA_CODE).orElse(null);
		Template template = schema == null ? null : templateRepository.findBySchemaIdAndIsDefaultTrue(schema.getId())
				.or(() -> templateRepository.findBySchemaId(schema.getId()).stream().findFirst())
				.orElse(null);

		Topic topic = topicRepository.save(Topic.builder()
				.collection(collection)
				.children(new ArrayList<>())
				.name(TOPIC_NAME)
				.translation(TOPIC_TRANSLATION)
				.schema(schema)
				.activeTemplate(template)
				.items(new ArrayList<>())
				.build());

		user.setDefaultTopicId(topic.getId());
		userRepository.save(user);
		LOG.info("Created default topic {} in collection {} for userId {}", topic.getId(), collection.getId(), userId);
		return toDto(topic);
	}

	private Optional<Collection> findUserCollection(String name, Long userId) {
		return collectionRepository.findFirstByNameAndOwnerId(name, userId)
				.filter(c -> c.getType() == CollectionType.USER);
	}

	private static boolean ownsUserTopic(Topic topic, Long userId) {
		Collection collection = topic.getCollection();
		return collection != null
				&& collection.getType() == CollectionType.USER
				&& collection.getOwner() != null
				&& userId.equals(collection.getOwner().getId());
	}

	private static DefaultTopicDTO toDto(Topic topic) {
		Collection collection = topic.getCollection();
		return new DefaultTopicDTO(
				collection.getId(),
				collection.getName(),
				collection.getTranslation(),
				topic.getId(),
				topic.getName(),
				topic.getTranslation(),
				topic.getActiveTemplate() == null ? null : topic.getActiveTemplate().getId());
	}
}
