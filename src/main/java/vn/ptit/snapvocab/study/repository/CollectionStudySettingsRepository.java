package vn.ptit.snapvocab.study.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.ptit.snapvocab.study.domain.CollectionStudySettings;

public interface CollectionStudySettingsRepository extends JpaRepository<CollectionStudySettings, Long> {
    Optional<CollectionStudySettings> findByUserIdAndCollectionId(Long userId, Long collectionId);
    List<CollectionStudySettings> findByUserIdAndCollectionIdIn(Long userId, List<Long> collectionIds);
    @Query("SELECT s.collection.id FROM CollectionStudySettings s WHERE s.user.id = :userId")
    List<Long> findCollectionIdsByUserId(Long userId);
    @Modifying
    @Query("DELETE FROM CollectionStudySettings s WHERE s.collection.id = :collectionId")
    void deleteByCollectionId(Long collectionId);
}
