package vn.ptit.snapvocab.study.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import vn.ptit.snapvocab.study.domain.StudyDailyProgress;

@Repository
public interface StudyDailyProgressRepository extends JpaRepository<StudyDailyProgress, Long> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StudyDailyProgress d WHERE d.collection.id = :collectionId")
    int deleteAllByCollectionId(Long collectionId);
	@Modifying
	@Query(value = """
			INSERT INTO study_daily_progress (user_id, collection_id, study_date, created_at, updated_at)
			VALUES (:userId, :collectionId, :studyDate, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
			""", nativeQuery = true)
	int createIfMissing(@Param("userId") Long userId, @Param("collectionId") Long collectionId,
			@Param("studyDate") LocalDate studyDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT progress FROM StudyDailyProgress progress
			WHERE progress.user.id = :userId AND progress.collection.id = :collectionId
			  AND progress.studyDate = :studyDate
			""")
	Optional<StudyDailyProgress> findForUpdate(@Param("userId") Long userId,
			@Param("collectionId") Long collectionId, @Param("studyDate") LocalDate studyDate);

}
