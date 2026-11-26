package vn.ptit.snapvocab.vocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class DefaultTopicServiceTest {

	private static final Long USER_ID = 4L;

	@Mock
	private UserRepository userRepository;
	@Mock
	private CollectionRepository collectionRepository;
	@Mock
	private TopicRepository topicRepository;
	@Mock
	private SchemaRepository schemaRepository;
	@Mock
	private TemplateRepository templateRepository;
	@Mock
	private CollectionService collectionService;

	@InjectMocks
	private DefaultTopicService service;

	private User user(Long defaultTopicId) {
		User user = User.builder().id(USER_ID).email("learner@test.local").firstName("L").password("x").build();
		user.setDefaultTopicId(defaultTopicId);
		when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
		return user;
	}

	private static Collection collection(Long id, User owner) {
		return Collection.builder().id(id).name(DefaultTopicService.COLLECTION_NAME)
				.translation(DefaultTopicService.COLLECTION_TRANSLATION).type(CollectionType.USER).owner(owner).build();
	}

	private void defaultSchema() {
		Schema schema = Schema.builder().id(1L).code(DefaultTopicService.DEFAULT_SCHEMA_CODE).build();
		when(schemaRepository.findByCode(DefaultTopicService.DEFAULT_SCHEMA_CODE)).thenReturn(Optional.of(schema));
		when(templateRepository.findBySchemaIdAndIsDefaultTrue(1L))
				.thenReturn(Optional.of(Template.builder().id(11L).schema(schema).build()));
	}

	@Test
	void returnsTheStoredTopicWithoutCreatingAnything() {
		User user = user(50L);
		Topic stored = Topic.builder().id(50L).name("Mine").collection(collection(7L, user)).build();
		when(topicRepository.findById(50L)).thenReturn(Optional.of(stored));

		DefaultTopicDTO result = service.getOrCreate(USER_ID);

		assertThat(result.topicId()).isEqualTo(50L);
		assertThat(result.collectionId()).isEqualTo(7L);
		verify(topicRepository, never()).save(any());
		verify(collectionRepository, never()).save(any());
	}

	@Test
	void createsCollectionAndTopicOnFirstCallAndRemembersIt() {
		User user = user(null);
		when(collectionRepository.findFirstByNameAndOwnerId(DefaultTopicService.COLLECTION_NAME, USER_ID))
				.thenReturn(Optional.empty());
		when(collectionRepository.save(any())).thenAnswer(inv -> {
			Collection saved = inv.getArgument(0);
			saved.setId(7L);
			return saved;
		});
		when(topicRepository.save(any())).thenAnswer(inv -> {
			Topic saved = inv.getArgument(0);
			saved.setId(51L);
			return saved;
		});
		defaultSchema();

		DefaultTopicDTO result = service.getOrCreate(USER_ID);

		ArgumentCaptor<Topic> topic = ArgumentCaptor.forClass(Topic.class);
		verify(topicRepository).save(topic.capture());
		assertThat(topic.getValue().getName()).isEqualTo(DefaultTopicService.TOPIC_NAME);
		assertThat(topic.getValue().getCollection().getOwner()).isSameAs(user);
		assertThat(topic.getValue().getActiveTemplate().getId()).isEqualTo(11L);
		assertThat(user.getDefaultTopicId()).isEqualTo(51L);
		assertThat(result.topicId()).isEqualTo(51L);
		assertThat(result.activeTemplateId()).isEqualTo(11L);
		verify(collectionService).evictUserCollectionsCache(USER_ID);
	}

	@Test
	void replacesADeletedTopicInsideTheExistingCollection() {
		User user = user(50L);
		when(topicRepository.findById(50L)).thenReturn(Optional.empty());
		when(collectionRepository.findFirstByNameAndOwnerId(DefaultTopicService.COLLECTION_NAME, USER_ID))
				.thenReturn(Optional.of(collection(7L, user)));
		when(topicRepository.save(any())).thenAnswer(inv -> {
			Topic saved = inv.getArgument(0);
			saved.setId(52L);
			return saved;
		});
		when(schemaRepository.findByCode(DefaultTopicService.DEFAULT_SCHEMA_CODE)).thenReturn(Optional.empty());

		DefaultTopicDTO result = service.getOrCreate(USER_ID);

		assertThat(result.collectionId()).isEqualTo(7L);
		assertThat(result.topicId()).isEqualTo(52L);
		assertThat(user.getDefaultTopicId()).isEqualTo(52L);
		verify(collectionRepository, never()).save(any());
	}

	@Test
	void ignoresAStoredIdThatPointsAtSomeoneElsesTopic() {
		User user = user(60L);
		User other = User.builder().id(99L).email("other@test.local").firstName("O").password("x").build();
		when(topicRepository.findById(60L))
				.thenReturn(Optional.of(Topic.builder().id(60L).collection(collection(8L, other)).build()));
		when(collectionRepository.findFirstByNameAndOwnerId(DefaultTopicService.COLLECTION_NAME, USER_ID))
				.thenReturn(Optional.of(collection(7L, user)));
		when(topicRepository.save(any())).thenAnswer(inv -> {
			Topic saved = inv.getArgument(0);
			saved.setId(53L);
			return saved;
		});
		when(schemaRepository.findByCode(any())).thenReturn(Optional.empty());

		assertThat(service.getOrCreate(USER_ID).topicId()).isEqualTo(53L);
		assertThat(List.of(user.getDefaultTopicId())).containsExactly(53L);
	}
}
