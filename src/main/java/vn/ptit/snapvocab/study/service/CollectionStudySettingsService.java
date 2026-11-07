package vn.ptit.snapvocab.study.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.study.domain.CollectionStudySettings;
import vn.ptit.snapvocab.study.domain.dto.CollectionStudySettingsDTO;
import vn.ptit.snapvocab.study.domain.dto.UpdateCollectionStudySettingsRequest;
import vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionStudySettingsService {
    public static final int DEFAULT_NEW_PER_DAY = 20;
    public static final int DEFAULT_REVIEW_PER_DAY = 200;
    private final CollectionStudySettingsRepository repository;
    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;

    public Collection accessibleCollection(Long userId, Long collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));
        if (collection.getType() != CollectionType.SYSTEM
                && (collection.getOwner() == null || !collection.getOwner().getId().equals(userId))
                && !SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            throw new AccessDeniedException("You do not have access to this collection");
        }
        return collection;
    }

    public CollectionStudySettingsDTO get(Long userId, Long collectionId) {
        accessibleCollection(userId, collectionId);
        return repository.findByUserIdAndCollectionId(userId, collectionId)
                .map(s -> dto(userId, collectionId, s))
                .orElse(new CollectionStudySettingsDTO(collectionId, userId,
                        DEFAULT_NEW_PER_DAY, DEFAULT_REVIEW_PER_DAY, false));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CollectionStudySettingsDTO update(Long userId, Long collectionId,
            UpdateCollectionStudySettingsRequest request) {
        Collection collection = accessibleCollection(userId, collectionId);
        // Same lock order as ratings: a settings update cannot race a first review.
        Economy.lockUser(userRepository, userId);
        CollectionStudySettings settings = repository.findByUserIdAndCollectionId(userId, collectionId)
                .orElseGet(CollectionStudySettings::new);
        settings.setUser(userRepository.getReferenceById(userId));
        settings.setCollection(collection);
        settings.setNewCardsPerDay(request.newCardsPerDay());
        settings.setReviewCardsPerDay(request.reviewCardsPerDay());
        return dto(userId, collectionId, repository.save(settings));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reset(Long userId, Long collectionId) {
        accessibleCollection(userId, collectionId);
        Economy.lockUser(userRepository, userId);
        repository.findByUserIdAndCollectionId(userId, collectionId).ifPresent(repository::delete);
    }

    private static CollectionStudySettingsDTO dto(Long userId, Long collectionId, CollectionStudySettings settings) {
        return new CollectionStudySettingsDTO(collectionId, userId,
                settings.getNewCardsPerDay(), settings.getReviewCardsPerDay(), true);
    }
}
