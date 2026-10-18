package vn.ptit.snapvocab.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.mission.domain.UserWordCredit;

@Repository
public interface UserWordCreditRepository extends JpaRepository<UserWordCredit, Long> {

	boolean existsByUserIdAndWordHash(Long userId, byte[] wordHash);
}
