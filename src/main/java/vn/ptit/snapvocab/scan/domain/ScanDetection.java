package vn.ptit.snapvocab.scan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One filtered label of a DONE scan, written with the DONE transition (admin_overview_backend.md §2.5 G2), so the
 * Admin can count labels without parsing {@code result_json}. Plain columns: read only by native stats queries.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
		name = "scan_detections",
		indexes = {
				@Index(name = "idx_scan_detections_created", columnList = "created_at"),
				@Index(name = "idx_scan_detections_label", columnList = "label, created_at"),
				@Index(name = "idx_scan_detections_scan", columnList = "scan_request_id")
		}
)
public class ScanDetection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scan_request_id", nullable = false, length = 36)
	private String scanRequestId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** The scan's own created_at, so detections and scans filter on the same instant. */
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Lower case, as ScanResultAssembler looks it up. */
	@Column(nullable = false, length = 120)
	private String label;

	@Column(length = 120)
	private String headword;

	@Column(nullable = false, length = 10)
	private String reliability;

	/** Label or headword was in the dictionary when the scan finished. */
	@Column(name = "has_word", nullable = false)
	private boolean hasWord;
}
