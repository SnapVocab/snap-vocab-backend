package vn.ptit.snapvocab.scan.domain;

import vn.ptit.snapvocab.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;

/**
 * One image recognition job (BF-06). Also the source of truth for the daily scan
 * quota: every row not in FAILED counts as one scan.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
		name = "scan_requests",
		indexes = {
				@Index(name = "idx_scan_requests_user_created", columnList = "user_id, created_at"),
				@Index(name = "idx_scan_requests_status", columnList = "status"),
				@Index(name = "idx_scan_requests_created", columnList = "created_at")
		}
)
public class ScanRequest extends BaseTimeEntity {

	/** UUID; also sent to the AI service as X-Request-Id so both logs share it. */
	@Id
	@Column(length = 36)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** Storage key of the uploaded image; null for the legacy synchronous endpoint. */
	@Column(name = "object_key", length = 512)
	private String objectKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ScanStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "error_code", length = 40)
	private ScanErrorCode errorCode;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	/**
	 * JSON of {@code ScanDetectionsDTO}: the AI's boxes, without dictionary data.
	 * Words are looked up when the result is read, so a word added to the
	 * dictionary later shows up in old results too.
	 */
	@Column(name = "result_json", columnDefinition = "LONGTEXT")
	private String resultJson;

	@Column(name = "model_version", length = 100)
	private String modelVersion;

	@Column(name = "detection_count")
	private Integer detectionCount;

	/** Time the AI service spent on the model, excluding its own queue. */
	@Column(name = "processing_time_ms")
	private Long processingTimeMs;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	/** The worker and the sweeper can both finish a job; the loser gets an optimistic lock failure. */
	@Version
	private Long version;
}

