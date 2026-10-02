package vn.ptit.snapvocab.scan.repository;


import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;

@Repository
public interface ScanRequestRepository extends JpaRepository<ScanRequest, String> {

	long countByUser_IdAndStatusInAndCreatedAtGreaterThanEqual(
			Long userId, Collection<ScanStatus> statuses, Instant since);

	Optional<ScanRequest> findByIdAndUser_Id(String id, Long userId);

	List<ScanRequest> findByStatusIn(Collection<ScanStatus> statuses);

	List<ScanRequest> findByStatusAndCreatedAtBefore(ScanStatus status, Instant cutoff);

	List<ScanRequest> findByStatusAndStartedAtBefore(ScanStatus status, Instant cutoff);
}

