package vn.ptit.snapvocab.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanStatusDTO;
import vn.ptit.snapvocab.domain.dto.scan.ScanSubmitResponse;
import vn.ptit.snapvocab.domain.dto.storage.PresignedUploadResponse;

public interface ScanService {

	/**
	 * Legacy synchronous scan: holds the request open until the AI answers.
	 *
	 * @deprecated use {@link #createUploadUrl}, {@link #submit} and {@link #getStatus}
	 */
	@Deprecated
	ScanResultDTO scanImage(MultipartFile image);

	/** Step 1: presigned PUT under {@code scans/{userId}/}. */
	PresignedUploadResponse createUploadUrl(String contentType);

	/** Step 2: charges one scan and queues the job; returns immediately. */
	ScanSubmitResponse submit(String objectKey);

	/** Step 3: poll until DONE or FAILED. Scans of other users are reported as not found. */
	ScanStatusDTO getStatus(String requestId);

	ScanQuotaDTO getQuota();
}
