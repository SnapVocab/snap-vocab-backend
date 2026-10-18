package vn.ptit.snapvocab.progress.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.progress.domain.LearningEvent;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;

@Repository
public interface LearningEventRepository extends JpaRepository<LearningEvent, Long> {

	boolean existsBySourceKey(String sourceKey);

	long countByUserIdAndEventType(Long userId, LearningEventType eventType);

	@Query("""
			SELECT e.learningDate AS learningDate, COUNT(e) AS eventCount
			FROM LearningEvent e
			WHERE e.user.id = :userId AND e.learningDate BETWEEN :from AND :to
			GROUP BY e.learningDate
			""")
	List<DailyCount> countPerDay(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	interface DailyCount {
		LocalDate getLearningDate();

		long getEventCount();
	}
}
