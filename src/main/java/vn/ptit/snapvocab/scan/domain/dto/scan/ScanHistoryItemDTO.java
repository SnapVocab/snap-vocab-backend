package vn.ptit.snapvocab.scan.domain.dto.scan;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;

/**
 * One row of "Lịch sử scan". A scan the learner stopped waiting for still finishes and counts against the quota, so
 * this is where they find it again; tapping it opens GET /api/scan/{requestId}.
 *
 * @param words      up to 4 distinct detected words, for the row's preview
 * @param imageUrl   short-lived download URL of the photo, only when DONE
 */
public record ScanHistoryItemDTO(
		String requestId,
		ScanStatus status,
		ScanErrorCode errorCode,
		Instant createdAt,
		Instant finishedAt,
		Integer detectionCount,
		List<String> words,
		String imageUrl
) {
}
