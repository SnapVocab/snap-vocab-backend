package vn.ptit.snapvocab.vocab.repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

@Repository
public interface FsrsRecordRepository extends JpaRepository<FsrsRecord, Long> {

	long countByUserIdAndStateNot(Long userId, CardState state);

	long countByUserIdAndStateAndStabilityLessThan(Long userId, CardState state, Float stability);

	long countByUserIdAndStateAndStabilityGreaterThanEqual(Long userId, CardState state, Float stability);

	long countByUserIdAndStateNotAndDueLessThanEqual(Long userId, CardState state, LocalDateTime due);
}
