package vn.ptit.snapvocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ptit.snapvocab.domain.Collection;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.collection.CollectionDTO;
import vn.ptit.snapvocab.domain.dto.collection.CreateCollectionDTO;
import vn.ptit.snapvocab.domain.dto.collection.UpdateCollectionDTO;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.domain.mapper.CollectionMapper;
import vn.ptit.snapvocab.repository.CollectionRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.util.constant.CacheConstants;
import vn.ptit.snapvocab.util.exception.ResourceAlreadyExistException;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

	@Mock
	private CollectionMapper collectionMapper;

	@Mock
	private CollectionRepository collectionRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private CacheManager cacheManager;

	@Mock
	private Cache userCollectionsCache;

	@Mock
	private Cache topicTreeCache;

	@InjectMocks
	private CollectionService collectionService;

	private User user1;
	private User user2;
	private Collection systemCol;
	private Collection user1Col;
	private CollectionDTO systemDto;
	private CollectionDTO user1Dto;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
		user1 = User.builder().id(1L).email("user1@snapvocab.com")
				.password("hashed_password_placeholder_for_testing_purposes_123456789012").build();
		user2 = User.builder().id(2L).email("user2@snapvocab.com")
				.password("hashed_password_placeholder_for_testing_purposes_123456789012").build();

		systemCol = Collection.builder()
				.id(10L)
				.name("Daily Conversation")
				.translation("Giao tiếp hàng ngày")
				.type(CollectionType.SYSTEM)
				.owner(null)
				.build();

		user1Col = Collection.builder()
				.id(20L)
				.name("My Private Words")
				.translation("Từ vựng của tôi")
				.type(CollectionType.USER)
				.owner(user1)
				.build();

		systemDto = CollectionDTO.builder()
				.id(10L)
				.name("Daily Conversation")
				.translation("Giao tiếp hàng ngày")
				.type(CollectionType.SYSTEM)
				.build();

		user1Dto = CollectionDTO.builder()
				.id(20L)
				.name("My Private Words")
				.translation("Từ vựng của tôi")
				.type(CollectionType.USER)
				.ownerId(1L)
				.build();
	}

	@org.junit.jupiter.api.AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getAllCollections_whenUserLoggedIn_returnsPublicAndUserCollectionsOnly() {
		when(collectionRepository.findByTypeOrOwnerId(CollectionType.SYSTEM, 1L))
				.thenReturn(List.of(systemCol, user1Col));
		when(collectionMapper.toDto(systemCol)).thenReturn(systemDto);
		when(collectionMapper.toDto(user1Col)).thenReturn(user1Dto);

		List<CollectionDTO> result = collectionService.getAllCollections(1L);

		assertThat(result).hasSize(2);
		assertThat(result).extracting(CollectionDTO::getName)
				.containsExactly("Daily Conversation", "My Private Words");
		verify(collectionRepository).findByTypeOrOwnerId(CollectionType.SYSTEM, 1L);
	}

	@Test
	void getAllCollections_whenUnauthenticated_returnsOnlyPublicCollections() {
		when(collectionRepository.findByType(CollectionType.SYSTEM))
				.thenReturn(List.of(systemCol));
		when(collectionMapper.toDto(systemCol)).thenReturn(systemDto);

		List<CollectionDTO> result = collectionService.getAllCollections(null);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getName()).isEqualTo("Daily Conversation");
		assertThat(result.get(0).getType()).isEqualTo(CollectionType.SYSTEM);
		verify(collectionRepository).findByType(CollectionType.SYSTEM);
		verify(collectionRepository, never()).findByTypeOrOwnerId(any(), any());
	}

	@Test
	void createUserCollection_evictsCacheForUserOnly() {
		CreateCollectionDTO dto = CreateCollectionDTO.builder()
				.name("My Travel")
				.translation("Du lịch")
				.type(CollectionType.USER)
				.build();

		when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
		when(collectionRepository.existsByNameAndOwnerId("My Travel", 1L)).thenReturn(false);

		Collection savedCol = Collection.builder()
				.id(30L)
				.name("My Travel")
				.translation("Du lịch")
				.type(CollectionType.USER)
				.owner(user1)
				.build();
		when(collectionRepository.save(any(Collection.class))).thenReturn(savedCol);
		when(collectionMapper.toDto(savedCol)).thenReturn(
				CollectionDTO.builder().id(30L).name("My Travel").type(CollectionType.USER).ownerId(1L)
						.build()
		);
		when(cacheManager.getCache(CacheConstants.USER_COLLECTIONS)).thenReturn(userCollectionsCache);

		CollectionDTO result = collectionService.createCollection(dto, 1L);

		assertThat(result.getId()).isEqualTo(30L);
		assertThat(result.getOwnerId()).isEqualTo(1L);

		// Verify evict is called for user 1 ONLY
		verify(userCollectionsCache).evict("1");
		verify(userCollectionsCache).evict(1L);
		verify(userCollectionsCache, never()).clear();
	}

	@Test
	void createCollection_duplicateNameForUser_throwsException() {
		CreateCollectionDTO dto = CreateCollectionDTO.builder()
				.name("My Private Words")
				.build();

		when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
		when(collectionRepository.existsByNameAndOwnerId("My Private Words", 1L)).thenReturn(true);

		assertThatThrownBy(() -> collectionService.createCollection(dto, 1L))
				.isInstanceOf(ResourceAlreadyExistException.class)
				.hasMessageContaining("already have a collection");
	}

	@Test
	void createSystemCollection_whenNotAdmin_throwsAccessDenied() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("user1", null,
						List.of(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
		);

		CreateCollectionDTO dto = CreateCollectionDTO.builder()
				.name("System Col")
				.type(CollectionType.SYSTEM)
				.build();

		when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

		assertThatThrownBy(() -> collectionService.createCollection(dto, 1L))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Only administrators");
	}

	@Test
	void createSystemCollection_whenAdmin_succeedsAndClearsAllUserCollectionsCache() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("admin", null,
						List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)))
		);

		CreateCollectionDTO dto = CreateCollectionDTO.builder()
				.name("New System Vocab")
				.type(CollectionType.SYSTEM)
				.build();

		when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
		when(collectionRepository.existsByNameAndType("New System Vocab",
				CollectionType.SYSTEM)).thenReturn(false);

		Collection savedCol = Collection.builder()
				.id(40L)
				.name("New System Vocab")
				.type(CollectionType.SYSTEM)
				.owner(null)
				.build();
		when(collectionRepository.save(any(Collection.class))).thenReturn(savedCol);
		when(collectionMapper.toDto(savedCol)).thenReturn(
				CollectionDTO.builder().id(40L).name("New System Vocab").type(CollectionType.SYSTEM).build()
		);
		when(cacheManager.getCache(CacheConstants.USER_COLLECTIONS)).thenReturn(userCollectionsCache);

		CollectionDTO result = collectionService.createCollection(dto, 1L);

		assertThat(result.getType()).isEqualTo(CollectionType.SYSTEM);
		// Admin creating system collection must clear cache for everyone
		verify(userCollectionsCache).clear();
	}

	@Test
	void updateCollection_byOwner_evictsUserOnly() {
		UpdateCollectionDTO updateDto = UpdateCollectionDTO.builder()
				.name("Updated Private Words")
				.translation("Từ vựng cập nhật")
				.build();

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(user1Col));
		when(collectionRepository.save(any(Collection.class))).thenReturn(user1Col);
		when(collectionMapper.toDto(user1Col)).thenReturn(user1Dto);
		when(cacheManager.getCache(CacheConstants.USER_COLLECTIONS)).thenReturn(userCollectionsCache);

		collectionService.updateCollection(20L, updateDto, 1L);

		verify(userCollectionsCache).evict("1");
		verify(userCollectionsCache).evict(1L);
		verify(userCollectionsCache, never()).clear();
	}

	@Test
	void updateCollection_byDifferentUser_throwsAccessDenied() {
		UpdateCollectionDTO updateDto = UpdateCollectionDTO.builder()
				.name("Hack Name")
				.build();

		when(collectionRepository.findById(20L)).thenReturn(Optional.of(user1Col));

		// User 2 trying to update User 1's collection
		assertThatThrownBy(() -> collectionService.updateCollection(20L, updateDto, 2L))
				.isInstanceOf(AccessDeniedException.class);

		verify(collectionRepository, never()).save(any());
	}

	@Test
	void deleteCollection_byOwner_evictsUserAndTopicTree() {
		when(collectionRepository.findById(20L)).thenReturn(Optional.of(user1Col));
		when(cacheManager.getCache(CacheConstants.USER_COLLECTIONS)).thenReturn(userCollectionsCache);
		when(cacheManager.getCache(CacheConstants.TOPIC_TREE)).thenReturn(topicTreeCache);

		collectionService.deleteCollection(20L, 1L);

		verify(collectionRepository).delete(user1Col);
		verify(userCollectionsCache).evict("1");
		verify(userCollectionsCache).evict(1L);
		verify(userCollectionsCache, never()).clear();
		verify(topicTreeCache).evict(20L);
	}

	@Test
	void deleteCollection_byDifferentUser_throwsAccessDenied() {
		when(collectionRepository.findById(20L)).thenReturn(Optional.of(user1Col));

		assertThatThrownBy(() -> collectionService.deleteCollection(20L, 2L))
				.isInstanceOf(AccessDeniedException.class);

		verify(collectionRepository, never()).delete(any());
	}
}
