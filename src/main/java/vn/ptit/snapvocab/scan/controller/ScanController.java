package vn.ptit.snapvocab.scan.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanHistoryItemDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanStatusDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanSubmitRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanSubmitResponse;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanUploadUrlRequest;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.scan.service.ScanService;

/**
 * Scan-to-vocabulary (BF-06).
 *
 * <p>Flow: {@code POST /upload-url} -> PUT the image to the returned URL ->
 * {@code POST /api/scan} with the objectName -> poll {@code GET /{requestId}}
 * every 2-3s until DONE or FAILED.
 *
 * <p>The multipart {@code POST /api/scan} (same path, told apart by
 * Content-Type) is the old synchronous flow, kept until the app moves over.
 */
@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {

	private final ScanService scanService;

	/** @deprecated synchronous; use upload-url + POST (JSON) + poll */
	@Deprecated
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ScanResultDTO> scan(@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(scanService.scanImage(file));
	}

	@PostMapping("/upload-url")
	public ResponseEntity<PresignedUploadResponse> createUploadUrl(@Valid @RequestBody ScanUploadUrlRequest request) {
		return ResponseEntity.ok(scanService.createUploadUrl(request.contentType()));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ScanSubmitResponse> submit(@Valid @RequestBody ScanSubmitRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(scanService.submit(request.objectKey()));
	}

	@GetMapping("/quota")
	public ResponseEntity<ScanQuotaDTO> quota() {
		return ResponseEntity.ok(scanService.getQuota());
	}

	/** "Lịch sử scan": the caller's scans, newest first, so a scan left before it finished can be found again. */
	@GetMapping("/history")
	public ResponseEntity<PaginationObject<List<ScanHistoryItemDTO>>> history(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(scanService.getHistory(page, size));
	}

	@GetMapping("/{requestId}")
	public ResponseEntity<ScanStatusDTO> status(@PathVariable String requestId) {
		return ResponseEntity.ok(scanService.getStatus(requestId));
	}
}

