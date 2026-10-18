package vn.ptit.snapvocab.mission.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.mission.domain.UserDailyMission;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;

@Repository
public interface UserDailyMissionRepository extends JpaRepository<UserDailyMission, Long> {

	boolean existsByUserIdAndMissionDate(Long userId, LocalDate missionDate);

	List<UserDailyMission> findByUserIdAndMissionDateOrderBySlot(Long userId, LocalDate missionDate);

	@Query("SELECT m.code FROM UserDailyMission m WHERE m.user.id = :userId AND m.missionDate = :date")
	List<String> findCodes(@Param("userId") Long userId, @Param("date") LocalDate date);

	/** Missions an action may advance (daily_mission.md §6.2); caller already holds the users lock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM UserDailyMission m WHERE m.user.id = :userId AND m.missionDate = :date "
			+ "AND m.status = vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus.IN_PROGRESS "
			+ "AND m.triggerEvent = :trigger ORDER BY m.slot")
	List<UserDailyMission> findOpenForUpdate(@Param("userId") Long userId, @Param("date") LocalDate date,
			@Param("trigger") MissionTriggerEvent trigger);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM UserDailyMission m WHERE m.id = :id AND m.user.id = :userId")
	Optional<UserDailyMission> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
