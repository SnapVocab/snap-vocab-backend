package vn.ptit.snapvocab.mission.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.mission.domain.UserDailyMissionClaim;

@Repository
public interface UserDailyMissionClaimRepository extends JpaRepository<UserDailyMissionClaim, Long> {

	Optional<UserDailyMissionClaim> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	Optional<UserDailyMissionClaim> findByUserDailyMissionId(Long userDailyMissionId);
}
