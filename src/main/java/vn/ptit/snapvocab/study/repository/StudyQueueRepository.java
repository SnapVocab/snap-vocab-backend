package vn.ptit.snapvocab.study.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

@Repository
public interface StudyQueueRepository extends JpaRepository<FsrsRecord, Long> {

    interface TopicStudyStats {
        Long getTopicId();
        Long getCollectionId();
        Long getNewCount();
        Long getDueCount();
        Long getLearningCount();
        Long getNewStarted();
        Long getWordCount();
    }

    /** One grouped read for the entire collection list, including collections never studied. */
    @Query("""
            SELECT ti.topic.id AS topicId, ti.topic.collection.id AS collectionId,
              SUM(CASE WHEN fr.id IS NULL AND ti.topic.hiddenAt IS NULL THEN 1 ELSE 0 END) AS newCount,
              SUM(CASE WHEN fr.due <= :now AND fr.state IN :states THEN 1 ELSE 0 END) AS dueCount,
              SUM(CASE WHEN fr.due <= :now AND fr.state IN :learningStates
                        AND fr.lastReview >= :dayStart THEN 1 ELSE 0 END) AS learningCount,
              SUM(CASE WHEN fr.createdAt >= :from AND fr.createdAt < :to THEN 1 ELSE 0 END) AS newStarted,
              SUM(CASE WHEN fr.id IS NOT NULL OR ti.topic.hiddenAt IS NULL THEN 1 ELSE 0 END) AS wordCount
            FROM TopicItem ti LEFT JOIN FsrsRecord fr ON fr.topicItem = ti AND fr.user.id = :userId
            WHERE ti.topic.collection.id IN :collectionIds
            GROUP BY ti.topic.id, ti.topic.collection.id
            """)
    List<TopicStudyStats> topicStudyStats(Long userId, List<Long> collectionIds, LocalDateTime now,
            LocalDateTime dayStart, Instant from, Instant to, List<CardState> states, List<CardState> learningStates);

    @Query("""
            SELECT fr FROM FsrsRecord fr
            WHERE fr.user.id = :userId AND fr.topicItem.topic.id IN :topicIds
              AND fr.due <= :now AND fr.state IN :learningStates AND fr.lastReview >= :dayStart
            ORDER BY fr.due, fr.id
            """)
    List<FsrsRecord> findIntradayLearning(Long userId, List<Long> topicIds, LocalDateTime now,
            LocalDateTime dayStart, List<CardState> learningStates, Pageable pageable);

    @Query("""
            SELECT fr FROM FsrsRecord fr
            WHERE fr.user.id = :userId AND fr.topicItem.topic.id IN :topicIds
              AND fr.due <= :now AND fr.state IN :states
              AND (fr.state NOT IN :learningStates OR fr.lastReview IS NULL OR fr.lastReview < :dayStart)
            ORDER BY fr.due, fr.id
            """)
    List<FsrsRecord> findQuotaReviews(Long userId, List<Long> topicIds, LocalDateTime now,
            LocalDateTime dayStart, List<CardState> states, List<CardState> learningStates, Pageable pageable);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM FsrsRecord fr WHERE fr.topicItem.id = :topicItemId")
	int deleteAllByTopicItemId(@Param("topicItemId") Long topicItemId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM FsrsRecord fr WHERE fr.topicItem.topic.collection.id = :collectionId")
    int deleteAllByCollectionId(Long collectionId);

	@Query("""
			SELECT fr FROM FsrsRecord fr
			WHERE fr.user.id = :userId
			  AND fr.topicItem.topic.id IN :topicIds
			  AND fr.due <= :now
			  AND fr.state IN :states
			ORDER BY fr.due ASC, fr.id ASC
			""")
	List<FsrsRecord> findDueRecords(
			@Param("userId") Long userId,
			@Param("topicIds") List<Long> topicIds,
			@Param("now") LocalDateTime now,
			@Param("states") List<CardState> states,
			Pageable pageable);

	@Query("""
			SELECT ti FROM TopicItem ti
			WHERE ti.topic.id IN :topicIds AND ti.topic.hiddenAt IS NULL
			  AND NOT EXISTS (
				SELECT fr.id FROM FsrsRecord fr
				WHERE fr.topicItem = ti AND fr.user.id = :userId
			  )
			ORDER BY ti.id ASC
			""")
	List<TopicItem> findNewItems(
			@Param("userId") Long userId,
			@Param("topicIds") List<Long> topicIds,
			Pageable pageable);

	@Query("""
			SELECT COUNT(fr) FROM FsrsRecord fr
			WHERE fr.user.id = :userId
			  AND fr.topicItem.topic.id IN :topicIds
			  AND fr.due <= :now
			  AND fr.state IN :states
			""")
	long countDue(
			@Param("userId") Long userId,
			@Param("topicIds") List<Long> topicIds,
			@Param("now") LocalDateTime now,
			@Param("states") List<CardState> states);

	@Query("""
			SELECT COUNT(fr) FROM FsrsRecord fr
			WHERE fr.user.id = :userId AND fr.due <= :now AND fr.state IN :states
			""")
	long countDueAll(
			@Param("userId") Long userId,
			@Param("now") LocalDateTime now,
			@Param("states") List<CardState> states);

	@Query("""
			SELECT COUNT(ti) FROM TopicItem ti
			WHERE ti.topic.id IN :topicIds AND ti.topic.hiddenAt IS NULL
			  AND NOT EXISTS (
				SELECT fr.id FROM FsrsRecord fr
				WHERE fr.topicItem = ti AND fr.user.id = :userId
			  )
			""")
	long countNewItems(@Param("userId") Long userId, @Param("topicIds") List<Long> topicIds);

	/** A record is created by a card's first review, so this counts new words started in the window. */
	long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant from, Instant to);

	interface StudiedCollection {
		Long getCollectionId();

		Long getDue();

		LocalDateTime getLastReview();
	}

	@Query("""
			SELECT fr.topicItem.topic.collection.id AS collectionId,
			       SUM(CASE WHEN fr.due <= :now AND fr.state IN :states THEN 1 ELSE 0 END) AS due,
			       MAX(fr.lastReview) AS lastReview
			FROM FsrsRecord fr
			WHERE fr.user.id = :userId
			GROUP BY fr.topicItem.topic.collection.id
			""")
	List<StudiedCollection> findStudiedCollections(
			@Param("userId") Long userId,
			@Param("now") LocalDateTime now,
			@Param("states") List<CardState> states);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT fr FROM FsrsRecord fr
			WHERE fr.user.id = :userId AND fr.topicItem.id = :topicItemId
			""")
	Optional<FsrsRecord> findForUpdate(
			@Param("userId") Long userId,
			@Param("topicItemId") Long topicItemId);
}
