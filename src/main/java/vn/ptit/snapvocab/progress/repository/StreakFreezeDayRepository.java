package vn.ptit.snapvocab.progress.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.progress.domain.StreakFreezeDay;

@Repository
public interface StreakFreezeDayRepository extends JpaRepository<StreakFreezeDay, Long> {

	/** Last covered day; null before the first one. */
	@Query("SELECT MAX(d.frozenDate) FROM StreakFreezeDay d WHERE d.user.id = :userId")
	LocalDate findLastFrozenDate(@Param("userId") Long userId);
}
