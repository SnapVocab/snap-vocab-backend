package vn.ptit.snapvocab.vocab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.dto.collection.CollectionDTO;
import vn.ptit.snapvocab.vocab.domain.dto.collection.CreateCollectionDTO;
import vn.ptit.snapvocab.vocab.domain.dto.collection.UpdateCollectionDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.mapper.CollectionMapper;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.constant.CacheConstants;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {

	private static final Logger LOG = LoggerFactory.getLogger(CollectionService.class);

	public static final String DEFAULT_USER_COLLECTION_NAME = "Bộ từ của tôi";
	public static final String DEFAULT_USER_COLLECTION_TRANSLATION = "My Vocabulary";

	/** Audit "changes" (admin_audit_log_backend.md §4.4.4). */
	private static final List<String> AUDIT_FIELDS = List.of("name", "translation");
	private static final List<String> DELETE_FIELDS = List.of("name", "topicCount", "itemCount");

	private final CollectionMapper collectionMapper;
	private final CollectionRepository collectionRepository;
	private final UserRepository userRepository;
	private final CacheManager cacheManager;
	private final TopicRepository topicRepository;
	private final TopicItemRepository topicItemRepository;
	private final AdminAuditService audit;
	private final ObjectMapper objectMapper;
    private final vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository collectionStudySettingsRepository;
    private final vn.ptit.snapvocab.study.repository.StudyDailyCardRepository studyDailyCardRepository;
    private final vn.ptit.snapvocab.study.repository.StudyDailyProgressRepository studyDailyProgressRepository;
    private final vn.ptit.snapvocab.study.repository.StudyQueueRepository studyQueueRepository;

	/**
	 * Writes the admin audit log for: system content, or an admin editing a learner's collection. A learner on their
	 * own collection is not audited (admin_audit_log_backend.md §4.4.4).
	 */
	static boolean auditable(Collection c) {
		if (c.getType() == CollectionType.SYSTEM) {
			return true;
		}
		Long me = SecurityUtils.getCurrentUserId().orElse(null);
		return SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)
				&& c.getOwner() != null && !c.getOwner().getId().equals(me);
	}

	private static AuditTarget target(Collection c) {
		return AuditTarget.of(AuditTargetType.COLLECTION, c.getId(), c.getName(), null);
	}

	/**
	 * Returns public (system) collections plus the collections created by the specified user. If
	 * userId is null, returns only public collections. Cached per user key; evictions for user
	 * collections affect that user only.
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
				.orElseThrow(
						() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		validateCollectionReadAccess(collection, userId);

		return collectionMapper.toDto(collection);
	}

	/**
	 * Ensures a newly registered (or activated) user has a default personal collection ready to use.
	 * Idempotent: does nothing if the user already owns at least one collection.
	 */
	@Transactional
	public CollectionDTO ensureDefaultUserCollection(User user) {
		if (user == null || user.getId() == null) {
			return null;
		}
		List<Collection> existing = this.collectionRepository.findByOwnerId(user.getId());
		if (!existing.isEmpty()) {
			return collectionMapper.toDto(existing.get(0));
		}
		Collection collection = Collection.builder()
				.name(DEFAULT_USER_COLLECTION_NAME)
				.translation(DEFAULT_USER_COLLECTION_TRANSLATION)
				.type(CollectionType.USER)
				.owner(user)
				.build();
		Collection saved = this.collectionRepository.save(collection);
		evictUserCollectionsCache(user.getId());
		LOG.info("Created default personal collection ID={} for user ID={}", saved.getId(), user.getId());
		return collectionMapper.toDto(saved);
	}

	/**
	 * Creates a new collection. If type is SYSTEM, requires ROLE_ADMIN and evicts all user collection
	 * caches. If type is USER, creates for current user and evicts cache for this user only.
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

			if (this.collectionRepository.existsByNameAndType(createCollectionDTO.getName(),
					CollectionType.SYSTEM)) {
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
			CollectionDTO dto = collectionMapper.toDto(saved);
			audit.record(AuditAction.COLLECTION_CREATED, target(saved), AuditDiff.of(objectMapper, null, dto, AUDIT_FIELDS),
					null, null, null);
			evictAllUserCollectionsCache();
			LOG.info("Created system collection ID: {}", saved.getId());
			return dto;
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
	 * Updates a collection. If SYSTEM: requires ROLE_ADMIN. If USER: requires owner or ROLE_ADMIN.
	 * Evicts cache for user only.
	 */
	@Transactional
	public CollectionDTO updateCollection(Long collectionId, UpdateCollectionDTO updateCollectionDTO,
			Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to update a collection");
		}

		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(
						() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		if (collection.getType() == CollectionType.SYSTEM) {
			if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
				throw new AccessDeniedException("Only administrators can update system collections");
			}
		} else {
			validateCollectionWriteAccess(collection, userId);
		}
		CollectionDTO before = collectionMapper.toDto(collection);
		collection.setName(updateCollectionDTO.getName());
		collection.setTranslation(updateCollectionDTO.getTranslation());
		Collection updated = this.collectionRepository.save(collection);
		CollectionDTO after = collectionMapper.toDto(updated);
		if (auditable(updated)) {
			List<AuditChange> changes = AuditDiff.of(objectMapper, before, after, AUDIT_FIELDS);
			if (!changes.isEmpty()) {
				audit.record(AuditAction.COLLECTION_UPDATED, target(updated), changes, null, null, null);
			}
		}
		if (collection.getType() == CollectionType.SYSTEM) {
			evictAllUserCollectionsCache();
		} else {
			evictUserCollectionsCache(collection.getOwner() != null ? collection.getOwner().getId() : userId);
		}
		return after;
	}

	/**
	 * Deletes a collection. If SYSTEM: requires ROLE_ADMIN. If USER: requires owner or ROLE_ADMIN.
	 * Evicts cache for user only.
	 */
	@Transactional
	public void deleteCollection(Long collectionId, Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to delete a collection");
		}

		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(
						() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		if (collection.getType() != CollectionType.SYSTEM) {
			validateCollectionWriteAccess(collection, userId);
		} else if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
			throw new AccessDeniedException("Only administrators can delete system collections");
		}
		if (auditable(collection)) {
			List<Long> topicIds = topicRepository.findByCollectionId(collectionId).stream().map(Topic::getId).toList();
			Map<String, Object> snapshot = new LinkedHashMap<>();
			snapshot.put("name", collection.getName());
			snapshot.put("topicCount", topicIds.size());
			snapshot.put("itemCount", topicIds.isEmpty() ? 0 : topicItemRepository.countByTopicIdIn(topicIds));
			audit.record(AuditAction.COLLECTION_DELETED, target(collection),
					AuditDiff.of(objectMapper, snapshot, null, DELETE_FIELDS), null, null, null);
		}

        // All learners' settings for a shared collection must disappear too.
        collectionStudySettingsRepository.deleteByCollectionId(collectionId);
        studyDailyCardRepository.deleteAllByCollectionId(collectionId);
        studyDailyProgressRepository.deleteAllByCollectionId(collectionId);
        studyQueueRepository.deleteAllByCollectionId(collectionId);
        if (collection.getType() == CollectionType.SYSTEM) {
            this.collectionRepository.delete(collection);
			evictAllUserCollectionsCache();
			evictTopicTreeCache(collectionId);
			LOG.info("Deleted system collection ID: {}", collectionId);
		} else {
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
				throw new AccessDeniedException(
						"Authentication required to access this private collection");
			}
			boolean isOwner =
					collection.getOwner() != null && collection.getOwner().getId().equals(userId);
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

