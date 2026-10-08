package vn.ptit.snapvocab.badge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.badge.domain.UserBadgeWordCredit;

@Repository
public interface UserBadgeWordCreditRepository extends JpaRepository<UserBadgeWordCredit, Long> {

	boolean existsByUserIdAndWordHash(Long userId, byte[] wordHash);

	long countByUserId(Long userId);
}
