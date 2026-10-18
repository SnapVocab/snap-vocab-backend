package vn.ptit.snapvocab.shop.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.shop.domain.BoosterActivation;

@Repository
public interface BoosterActivationRepository extends JpaRepository<BoosterActivation, Long> {

	/** Active ⇔ expiresAt > now (lazy expiry, no status column). At most one exists per user (§7.3). */
	Optional<BoosterActivation> findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(Long userId, Instant now);

	Optional<BoosterActivation> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
