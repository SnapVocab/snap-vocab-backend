package vn.ptit.snapvocab.badge.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.badge.domain.UserBadge;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

	List<UserBadge> findByUserId(Long userId);

	long countByUserIdAndSeenAtIsNull(Long userId);

	long countByBadgeId(Long badgeId);

	/** One aggregate for the entire Admin list; never loads learner records or counts each badge separately. */
	@Query("SELECT ub.badge.id AS badgeId, COUNT(ub) AS earnerCount FROM UserBadge ub GROUP BY ub.badge.id")
	List<BadgeEarnerCount> countEarnersByBadge();

	interface BadgeEarnerCount {
		Long getBadgeId();

		long getEarnerCount();
	}

	/** Ids not owned, not earned or already seen are skipped (badges.md §8.3). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE UserBadge ub SET ub.seenAt = :now "
			+ "WHERE ub.user.id = :userId AND ub.badge.id IN :badgeIds AND ub.seenAt IS NULL")
	int markSeen(@Param("userId") Long userId, @Param("badgeIds") Collection<Long> badgeIds, @Param("now") Instant now);
}
