package vn.ptit.snapvocab.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.mission.domain.UserDailyMissionProgressLog;

@Repository
public interface UserDailyMissionProgressLogRepository extends JpaRepository<UserDailyMissionProgressLog, Long> {

	boolean existsByUserDailyMissionIdAndSourceKey(Long userDailyMissionId, String sourceKey);
}
