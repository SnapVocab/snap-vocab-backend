package vn.ptit.snapvocab.scan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.ptit.snapvocab.scan.domain.ScanDetection;

public interface ScanDetectionRepository extends JpaRepository<ScanDetection, Long> {
}
