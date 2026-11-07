package vn.ptit.snapvocab.study.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.study.domain.StudyDailyCard;

@Repository
public interface StudyDailyCardRepository extends JpaRepository<StudyDailyCard, Long> {
    interface ReviewUsage {
        Long getCollectionId();
        Long getReviewCount();
    }

    /** New introductions consume only the new quota; intraday repetitions never consume it twice. */
    @Query("""
            SELECT d.collection.id AS collectionId, COUNT(d) AS reviewCount
            FROM StudyDailyCard d JOIN FsrsRecord fr ON fr.topicItem = d.topicItem AND fr.user = d.user
            WHERE d.user.id = :userId AND d.studyDate = :day AND d.collection.id IN :collectionIds
              AND fr.createdAt < :dayStart
            GROUP BY d.collection.id
            """)
    List<ReviewUsage> reviewUsage(Long userId, List<Long> collectionIds, LocalDate day, java.time.Instant dayStart);
	long countByUserIdAndStudyDate(Long userId, LocalDate studyDate);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM StudyDailyCard dailyCard WHERE dailyCard.topicItem.id = :topicItemId")
	int deleteAllByTopicItemId(@Param("topicItemId") Long topicItemId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StudyDailyCard d WHERE d.collection.id = :collectionId")
    int deleteAllByCollectionId(Long collectionId);

	long countByUserIdAndCollectionIdAndStudyDate(Long userId, Long collectionId, LocalDate studyDate);

	@Query("""
			SELECT dailyCard.topicItem.id FROM StudyDailyCard dailyCard
			WHERE dailyCard.user.id = :userId AND dailyCard.collection.id = :collectionId
			  AND dailyCard.studyDate = :studyDate
			""")
	List<Long> findTopicItemIdsStudiedToday(@Param("userId") Long userId,
			@Param("collectionId") Long collectionId, @Param("studyDate") LocalDate studyDate);

	boolean existsByUserIdAndCollectionIdAndStudyDateAndTopicItemId(
			Long userId, Long collectionId, LocalDate studyDate, Long topicItemId);
}
