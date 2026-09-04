package vn.ptit.snapvocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ptit.snapvocab.domain.Collection;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.repository.CollectionRepository;
import vn.ptit.snapvocab.repository.TopicAttributeGroupRepository;
import vn.ptit.snapvocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.repository.TopicRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.DomainUserDetailsService.UserWithId;

@ExtendWith(MockitoExtension.class)
class TopicServiceCollectionTest {

	@Mock
	private TopicItemRepository topicItemRepository;

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private CollectionRepository collectionRepository;

	@Mock
	private TopicAttributeGroupRepository topicAttributeGroupRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private TopicMapper topicMapper;

	@InjectMocks
	private TopicService topicService;

	private User user1;
	private Collection systemCollection;
	private Collection userCollection;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
		user1 = User.builder()
				.id(1L)
				.email("user1@snapvocab.com")
				.password("hashed_password_placeholder_for_testing_purposes_123456789012")
				.authorities(new java.util.HashSet<>())
				.build();

		systemCollection = Collection.builder()
				.id(10L)
				.name("System Col")
				.type(CollectionType.SYSTEM)
				.build();

		userCollection = Collection.builder()
				.id(20L)
				.name("User Col")
				.type(CollectionType.USER)
				.owner(user1)
				.build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getTopicsByCollection_systemCollection_allowedForAnyone() {
		when(collectionRepository.findById(10L)).thenReturn(Optional.of(systemCollection));
		when(topicRepository.findByCollectionId(10L)).thenReturn(List.of());

		List<TopicDTO> result = topicService.getTopicsByCollection(10L);

		assertThat(result).isEmpty();
		verify(topicRepository).findByCollectionId(10L);
	}

	@Test
	void getTopicsByCollection_userCollection_allowedForOwner() {
		UserWithId userWithId = UserWithId.fromUser(user1);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userWithId, null, List.of(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
		);

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));
		when(userRepository.findOneByEmailIgnoreCase("user1@snapvocab.com")).thenReturn(Optional.of(user1));
		when(topicRepository.findByCollectionId(20L)).thenReturn(List.of());

		List<TopicDTO> result = topicService.getTopicsByCollection(20L);

		assertThat(result).isEmpty();
		verify(topicRepository).findByCollectionId(20L);
	}

	@Test
	void getTopicsByCollection_userCollection_unauthenticated_throwsAccessDenied() {
		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));

		assertThatThrownBy(() -> topicService.getTopicsByCollection(20L))
				.isInstanceOf(AccessDeniedException.class);

		verify(topicRepository, never()).findByCollectionId(any());
	}

	@Test
	void createCollectionTopic_userCollection_byOwner_succeedsAndSaves() {
		UserWithId userWithId = UserWithId.fromUser(user1);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userWithId, null, List.of(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
		);

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));
		when(userRepository.findOneByEmailIgnoreCase("user1@snapvocab.com")).thenReturn(Optional.of(user1));

		Topic savedTopic = Topic.builder().id(100L).name("My New Topic").build();
		when(topicRepository.save(any(Topic.class))).thenReturn(savedTopic);
		when(topicMapper.toDto(savedTopic)).thenReturn(TopicDTO.builder().id(100L).name("My New Topic").build());

		CreateTopicDTO dto = CreateTopicDTO.builder().name("My New Topic").build();
		TopicDTO result = topicService.createCollectionTopic(20L, dto);

		assertThat(result.getId()).isEqualTo(100L);
		assertThat(result.getName()).isEqualTo("My New Topic");
		verify(topicRepository).save(any(Topic.class));
	}
}
