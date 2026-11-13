package vn.ptit.snapvocab.scan.repository;


import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	/** ScanDetectionBackfill page: DONE scans with no scan_detections row, oldest first, after the keyset cursor. */
	@Query(value = """
			SELECT s.* FROM scan_requests s
			WHERE s.status = 'DONE'
			  AND (s.created_at > :afterCreated OR (s.created_at = :afterCreated AND s.id > :afterId))
			  AND NOT EXISTS (SELECT 1 FROM scan_detections d WHERE d.scan_request_id = s.id)
			ORDER BY s.created_at, s.id
			LIMIT 500
			""", nativeQuery = true)
	List<ScanRequest> findDoneWithoutDetections(@Param("afterCreated") Instant afterCreated,
			@Param("afterId") String afterId);
}

