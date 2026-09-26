package vn.ptit.snapvocab.scan.domain.dto.scan;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;

/**
 * Poll response for GET /api/scan/{requestId}. {@code result} is set only when
 * DONE, {@code errorCode} only when FAILED.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanStatusDTO {

	private String requestId;
	private ScanStatus status;
	private ScanErrorCode errorCode;
	private Instant createdAt;
	private Instant finishedAt;
	private ScanResultDTO result;
}

