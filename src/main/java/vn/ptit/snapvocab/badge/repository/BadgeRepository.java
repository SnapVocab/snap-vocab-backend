package vn.ptit.snapvocab.badge.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.badge.domain.Badge;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {

	Optional<Badge> findByCode(String code);

	List<Badge> findAllByOrderBySortOrderAscIdAsc();

	// ---- refill estimate (PD-BDG-13d): one aggregate per call, metrics exactly as BadgeService.metrics reads them ----

	String NOT_YET_EARNED = "u.activated = true AND NOT EXISTS "
			+ "(SELECT 1 FROM UserBadge ub WHERE ub.user = u AND ub.badge.id = :badgeId) AND ";

	@Query("SELECT COUNT(u) FROM User u WHERE " + NOT_YET_EARNED + "u.longestStreakDays >= :threshold")
	long countRefillLongestStreak(@Param("badgeId") Long badgeId, @Param("threshold") int threshold);

	/** Same condition as QuizRepository.countCompleted. */
	@Query("SELECT COUNT(u) FROM User u WHERE " + NOT_YET_EARNED
			+ "(SELECT COUNT(q) FROM Quiz q WHERE q.user = u "
			+ "AND q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.COMPLETED) >= :threshold")
	long countRefillQuizCompleted(@Param("badgeId") Long badgeId, @Param("threshold") long threshold);

	/** Same condition as QuizRepository.countPerfect. */
	@Query("SELECT COUNT(u) FROM User u WHERE " + NOT_YET_EARNED
			+ "(SELECT COUNT(q) FROM Quiz q WHERE q.user = u "
			+ "AND q.status = vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus.COMPLETED "
			+ "AND q.correctCount = q.totalUnits) >= :threshold")
	long countRefillPerfectQuiz(@Param("badgeId") Long badgeId, @Param("threshold") long threshold);

	/** Same rows as UserBadgeWordCreditRepository.countByUserId. */
	@Query("SELECT COUNT(u) FROM User u WHERE " + NOT_YET_EARNED
			+ "(SELECT COUNT(c) FROM UserBadgeWordCredit c WHERE c.user = u) >= :threshold")
	long countRefillDistinctWords(@Param("badgeId") Long badgeId, @Param("threshold") long threshold);
}
