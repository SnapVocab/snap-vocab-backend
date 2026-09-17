package vn.ptit.snapvocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ptit.snapvocab.domain.Collection;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.domain.mapper.TopicSchemaMapper;
import vn.ptit.snapvocab.repository.CollectionRepository;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.repository.TemplateRepository;
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
	private SchemaRepository schemaRepository;

	@Mock
	private SchemaAttributeGroupRepository schemaAttributeGroupRepository;

	@Mock
	private TemplateRepository templateRepository;

	@Mock
	private TemplateFieldRepository templateFieldRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private TopicMapper topicMapper;

	@Mock
	private TopicSchemaMapper topicSchemaMapper;

	@Mock
	private SchemaMapper schemaMapper;

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
		Pageable pageable = PageRequest.of(0, 10);
		when(collectionRepository.findById(10L)).thenReturn(Optional.of(systemCollection));
		when(topicRepository.findByCollectionIdAndParentIsNull(10L, pageable)).thenReturn(Page.empty());

		PaginationObject<List<TopicDTO>> result = topicService.getTopicsByCollection(10L, null, pageable);

		assertThat(result.getData()).isEmpty();
		verify(topicRepository).findByCollectionIdAndParentIsNull(10L, pageable);
	}

	@Test
	void getTopicsByCollection_userCollection_allowedForOwner() {
		Pageable pageable = PageRequest.of(0, 10);
		UserWithId userWithId = UserWithId.fromUser(user1);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userWithId, null,
						List.of(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
		);

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));
		when(userRepository.findOneByEmailIgnoreCase("user1@snapvocab.com")).thenReturn(
				Optional.of(user1));
		when(topicRepository.findByCollectionIdAndParentIsNull(20L, pageable)).thenReturn(Page.empty());

		PaginationObject<List<TopicDTO>> result = topicService.getTopicsByCollection(20L, null, pageable);

		assertThat(result.getData()).isEmpty();
		verify(topicRepository).findByCollectionIdAndParentIsNull(20L, pageable);
	}

	@Test
	void getTopicsByCollection_userCollection_unauthenticated_throwsAccessDenied() {
		Pageable pageable = PageRequest.of(0, 10);
		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));

		assertThatThrownBy(() -> topicService.getTopicsByCollection(20L, null, pageable))
				.isInstanceOf(AccessDeniedException.class);

		verify(topicRepository, never()).findByCollectionIdAndParentIsNull(any(), any());
	}

	@Test
	void getTopicsByCollection_withParentId_queriesByParentIdAndReturnsDTOs() {
		Pageable pageable = PageRequest.of(0, 10);
		when(collectionRepository.findById(10L)).thenReturn(Optional.of(systemCollection));
		TopicDTO topicDto = TopicDTO.builder().id(100L).name("Topic 1").hasChildren(true).build();
		when(topicRepository.findByCollectionIdAndParentId(10L, 5L, pageable))
				.thenReturn(new PageImpl<>(List.of(topicDto), pageable, 1));

		PaginationObject<List<TopicDTO>> result = topicService.getTopicsByCollection(10L, 5L, pageable);

		assertThat(result.getData()).hasSize(1);
		assertThat(result.getData().get(0).getHasChildren()).isTrue();
		verify(topicRepository).findByCollectionIdAndParentId(10L, 5L, pageable);
	}

	@Test
	void createCollectionTopic_userCollection_byOwner_succeedsAndSaves() {
		UserWithId userWithId = UserWithId.fromUser(user1);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userWithId, null,
						List.of(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
		);

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));
		when(userRepository.findOneByEmailIgnoreCase("user1@snapvocab.com")).thenReturn(
				Optional.of(user1));

		Topic savedTopic = Topic.builder().id(100L).name("My New Topic").build();
		when(topicRepository.save(any(Topic.class))).thenReturn(savedTopic);
		when(schemaRepository.save(any(vn.ptit.snapvocab.domain.Schema.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(topicMapper.toDto(savedTopic)).thenReturn(
				TopicDTO.builder().id(100L).name("My New Topic").build());

		CreateTopicDTO dto = CreateTopicDTO.builder().name("My New Topic").build();
		TopicDTO result = topicService.createCollectionTopic(20L, dto);

		assertThat(result.getId()).isEqualTo(100L);
		assertThat(result.getName()).isEqualTo("My New Topic");
		verify(topicRepository).save(any(Topic.class));
	}

	@Test
	void createCollectionTopic_whenSchemaIdNull_usesSystemDefaultEnglishSchemaAndSetsActiveTemplate() {
		UserWithId userWithId = UserWithId.fromUser(user1);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userWithId, null,
						List.of(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
		);

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(userCollection));
		when(userRepository.findOneByEmailIgnoreCase("user1@snapvocab.com")).thenReturn(
				Optional.of(user1));

		vn.ptit.snapvocab.domain.Schema defaultEnglish = vn.ptit.snapvocab.domain.Schema.builder()
				.id(1L)
				.code("DEFAULT_ENGLISH")
				.isSystem(true)
				.name("Tiếng Anh Tiêu Chuẩn")
				.build();
		when(schemaRepository.findByCode("DEFAULT_ENGLISH")).thenReturn(Optional.of(defaultEnglish));

		vn.ptit.snapvocab.domain.Template defaultTemplate = vn.ptit.snapvocab.domain.Template.builder()
				.id(10L)
				.schema(defaultEnglish)
				.code("STANDARD")
				.name("Thẻ Chuẩn")
				.isDefault(true)
				.build();
		when(templateRepository.findBySchemaIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(defaultTemplate));

		when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> {
			Topic t = invocation.getArgument(0);
			t.setId(100L);
			return t;
		});
		when(topicMapper.toDto(any(Topic.class))).thenAnswer(invocation -> {
			Topic t = invocation.getArgument(0);
			return TopicDTO.builder()
					.id(t.getId())
					.name(t.getName())
					.activeTemplateId(t.getActiveTemplate() != null ? t.getActiveTemplate().getId() : null)
					.activeTemplateCode(t.getActiveTemplate() != null ? t.getActiveTemplate().getCode() : null)
					.build();
		});

		CreateTopicDTO dto = CreateTopicDTO.builder().name("My New Topic").build();
		TopicDTO result = topicService.createCollectionTopic(20L, dto);

		assertThat(result).isNotNull();
		assertThat(result.getActiveTemplateId()).isEqualTo(10L);
		assertThat(result.getActiveTemplateCode()).isEqualTo("STANDARD");
		verify(topicRepository).save(any(Topic.class));
	}
}
