package vn.ptit.snapvocab.quiz.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.quiz.domain.Quiz;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

	Optional<Quiz> findByIdAndUserId(Long id, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT q FROM Quiz q WHERE q.id = :id AND q.user.id = :userId")
	Optional<Quiz> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);

	Optional<Quiz> findByUserIdAndCreateIdempotencyKey(Long userId, String createIdempotencyKey);

	@Query(value = "SELECT q FROM Quiz q WHERE q.user.id = :userId ORDER BY q.createdAt DESC, q.id DESC", countQuery = "SELECT COUNT(q) FROM Quiz q WHERE q.user.id = :userId")
	Page<Quiz> findHistory(@Param("userId") Long userId, Pageable pageable);

	@Query("SELECT COUNT(q) AS quizCount, COALESCE(SUM(q.correctCount), 0) AS correctCount, "
			+ "COALESCE(SUM(q.totalUnits), 0) AS totalUnits FROM Quiz q WHERE q.user.id = :userId "
			+ "AND q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.COMPLETED")
	CompletedTotals completedTotals(@Param("userId") Long userId);

	interface CompletedTotals {
		long getQuizCount();

		long getCorrectCount();

		long getTotalUnits();
	}

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Quiz q SET q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.EXPIRED "
			+ "WHERE q.user.id = :userId "
			+ "AND q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.IN_PROGRESS "
			+ "AND q.createdAt < :cutoff")
	int expireStale(@Param("userId") Long userId, @Param("cutoff") Instant cutoff);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Quiz q SET q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.EXPIRED "
			+ "WHERE q.id = :quizId AND q.user.id = :userId "
			+ "AND q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.IN_PROGRESS "
			+ "AND q.createdAt < :cutoff")
	int expireStaleById(@Param("quizId") Long quizId, @Param("userId") Long userId,
			@Param("cutoff") Instant cutoff);
}

