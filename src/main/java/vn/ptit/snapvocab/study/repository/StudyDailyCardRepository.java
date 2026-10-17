package vn.ptit.snapvocab.study.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.study.domain.StudyDailyCard;

@Repository
public interface StudyDailyCardRepository extends JpaRepository<StudyDailyCard, Long> {
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
