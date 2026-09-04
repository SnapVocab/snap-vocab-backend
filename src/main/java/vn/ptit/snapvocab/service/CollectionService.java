package vn.ptit.snapvocab.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.util.constant.CacheConstants;
import vn.ptit.snapvocab.util.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {

	private static final Logger LOG = LoggerFactory.getLogger(CollectionService.class);

	private final CollectionMapper collectionMapper;
	private final CollectionRepository collectionRepository;
	private final UserRepository userRepository;
	private final CacheManager cacheManager;

	/**
	 * Returns public (system) collections plus the collections created by the specified user.
	 * If userId is null, returns only public collections.
	 * Cached per user key; evictions for user collections affect that user only.
	 */
	@Cacheable(
			cacheNames = CacheConstants.USER_COLLECTIONS,
			key = "#userId != null ? #userId.toString() : 'public'"
	)
	public List<CollectionDTO> getAllCollections(Long userId) {
		LOG.info("Fetching all collections for userId: {}", userId);
		List<Collection> collections;
		if (userId != null) {
			collections = this.collectionRepository.findByTypeOrOwnerId(CollectionType.SYSTEM, userId);
		} else {
			collections = this.collectionRepository.findByType(CollectionType.SYSTEM);
		}
		return collections.stream()
				.map(collectionMapper::toDto)
				.collect(Collectors.toList());
	}

	/**
	 * Returns only public (system) collections.
	 */
	public List<CollectionDTO> getSystemCollections() {
		return this.collectionRepository.findByType(CollectionType.SYSTEM).stream()
				.map(collectionMapper::toDto)
				.collect(Collectors.toList());
	}

	/**
	 * Returns only collections owned by the specified user.
	 */
	public List<CollectionDTO> getMyCollections(Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to view user collections");
		}
		return this.collectionRepository.findByOwnerId(userId).stream()
				.map(collectionMapper::toDto)
				.collect(Collectors.toList());
	}

	/**
	 * Returns a single collection by ID, enforcing access permissions.
	 */
	public CollectionDTO getCollectionById(Long collectionId, Long userId) {
		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		validateCollectionReadAccess(collection, userId);

		return collectionMapper.toDto(collection);
	}

	/**
	 * Creates a new collection.
	 * If type is SYSTEM, requires ROLE_ADMIN and evicts all user collection caches.
	 * If type is USER, creates for current user and evicts cache for this user only.
	 */
	@Transactional
	public CollectionDTO createCollection(CreateCollectionDTO createCollectionDTO, Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to create a collection");
		}

		User currentUser = this.userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

		CollectionType type = createCollectionDTO.getType() != null
				? createCollectionDTO.getType()
				: CollectionType.USER;

		if (type == CollectionType.SYSTEM) {
			if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
				throw new AccessDeniedException("Only administrators can create system collections");
			}

			if (this.collectionRepository.existsByNameAndType(createCollectionDTO.getName(), CollectionType.SYSTEM)) {
				throw new ResourceAlreadyExistException(
						"System collection with name '" + createCollectionDTO.getName() + "' already exists");
			}

			Collection collection = Collection.builder()
					.name(createCollectionDTO.getName())
					.translation(createCollectionDTO.getTranslation())
					.type(CollectionType.SYSTEM)
					.owner(null)
					.build();

			Collection saved = this.collectionRepository.save(collection);
			evictAllUserCollectionsCache();
			LOG.info("Created system collection ID: {}", saved.getId());
			return collectionMapper.toDto(saved);
		} else {
			if (this.collectionRepository.existsByNameAndOwnerId(createCollectionDTO.getName(), userId)) {
				throw new ResourceAlreadyExistException(
						"You already have a collection named '" + createCollectionDTO.getName() + "'");
			}

			Collection collection = Collection.builder()
					.name(createCollectionDTO.getName())
					.translation(createCollectionDTO.getTranslation())
					.type(CollectionType.USER)
					.owner(currentUser)
					.build();

			Collection saved = this.collectionRepository.save(collection);
			evictUserCollectionsCache(userId);
			LOG.info("Created user collection ID: {} for userId: {}", saved.getId(), userId);
			return collectionMapper.toDto(saved);
		}
	}

	/**
	 * Updates a collection.
	 * If SYSTEM: requires ROLE_ADMIN.
	 * If USER: requires owner or ROLE_ADMIN. Evicts cache for user only.
	 */
	@Transactional
	public CollectionDTO updateCollection(Long collectionId, UpdateCollectionDTO updateCollectionDTO, Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to update a collection");
		}

		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		if (collection.getType() == CollectionType.SYSTEM) {
			if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
				throw new AccessDeniedException("Only administrators can update system collections");
			}
			collection.setName(updateCollectionDTO.getName());
			collection.setTranslation(updateCollectionDTO.getTranslation());
			Collection updated = this.collectionRepository.save(collection);
			evictAllUserCollectionsCache();
			return collectionMapper.toDto(updated);
		} else {
			validateCollectionWriteAccess(collection, userId);
			collection.setName(updateCollectionDTO.getName());
			collection.setTranslation(updateCollectionDTO.getTranslation());
			Collection updated = this.collectionRepository.save(collection);
			Long ownerId = collection.getOwner() != null ? collection.getOwner().getId() : userId;
			evictUserCollectionsCache(ownerId);
			return collectionMapper.toDto(updated);
		}
	}

	/**
	 * Deletes a collection.
	 * If SYSTEM: requires ROLE_ADMIN.
	 * If USER: requires owner or ROLE_ADMIN. Evicts cache for user only.
	 */
	@Transactional
	public void deleteCollection(Long collectionId, Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to delete a collection");
		}

		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		if (collection.getType() == CollectionType.SYSTEM) {
			if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
				throw new AccessDeniedException("Only administrators can delete system collections");
			}
			this.collectionRepository.delete(collection);
			evictAllUserCollectionsCache();
			evictTopicTreeCache(collectionId);
			LOG.info("Deleted system collection ID: {}", collectionId);
		} else {
			validateCollectionWriteAccess(collection, userId);
			Long ownerId = collection.getOwner() != null ? collection.getOwner().getId() : userId;
			this.collectionRepository.delete(collection);
			evictUserCollectionsCache(ownerId);
			evictTopicTreeCache(collectionId);
			LOG.info("Deleted user collection ID: {} for userId: {}", collectionId, ownerId);
		}
	}

	/**
	 * Evicts cache for a single user only.
	 */
	public void evictUserCollectionsCache(Long userId) {
		if (userId == null) {
			return;
		}
		Cache cache = this.cacheManager.getCache(CacheConstants.USER_COLLECTIONS);
		if (cache != null) {
			cache.evict(userId.toString());
			cache.evict(userId);
			LOG.info("Evicted collections cache for user ID: {}", userId);
		}
	}

	/**
	 * Evicts all entries from the user collections cache (used when system collections change).
	 */
	public void evictAllUserCollectionsCache() {
		Cache cache = this.cacheManager.getCache(CacheConstants.USER_COLLECTIONS);
		if (cache != null) {
			cache.clear();
			LOG.info("Cleared all collections cache due to system collection modification");
		}
	}

	/**
	 * Evicts topic-tree cache for a collection.
	 */
	public void evictTopicTreeCache(Long collectionId) {
		if (collectionId == null) {
			return;
		}
		Cache cache = this.cacheManager.getCache(CacheConstants.TOPIC_TREE);
		if (cache != null) {
			cache.evict(collectionId);
			cache.evict(collectionId.toString());
			LOG.info("Evicted topic tree cache for collection ID: {}", collectionId);
		}
	}

	private void validateCollectionReadAccess(Collection collection, Long userId) {
		if (collection.getType() == CollectionType.USER) {
			if (userId == null) {
				throw new AccessDeniedException("Authentication required to access this private collection");
			}
			boolean isOwner = collection.getOwner() != null && collection.getOwner().getId().equals(userId);
			boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
			if (!isOwner && !isAdmin) {
				throw new AccessDeniedException("You do not have permission to access this collection");
			}
		}
	}

	private void validateCollectionWriteAccess(Collection collection, Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to modify this collection");
		}
		boolean isOwner = collection.getOwner() != null && collection.getOwner().getId().equals(userId);
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		if (!isOwner && !isAdmin) {
			throw new AccessDeniedException("You do not have permission to modify this collection");
		}
	}
}
