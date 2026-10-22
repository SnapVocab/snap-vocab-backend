package vn.ptit.snapvocab.study.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

@Repository
public interface StudyQueueRepository extends JpaRepository<FsrsRecord, Long> {

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
			WHERE ti.topic.id IN :topicIds
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
			WHERE ti.topic.id IN :topicIds
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
