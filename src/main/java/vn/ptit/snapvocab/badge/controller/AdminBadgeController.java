package vn.ptit.snapvocab.badge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.badge.domain.dto.AdminBadgeDTO;
import vn.ptit.snapvocab.badge.domain.dto.RefillEstimateDTO;
import vn.ptit.snapvocab.badge.service.AdminBadgeService;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.domain.dto.UploadUrlRequest;

/** badges.md §8.4 (P1 + P2). SecurityConfiguration requires ROLE_ADMIN for /api/admin/**. No badge create/delete (P3). */
@RestController
@RequestMapping("/api/admin/badges")
@RequiredArgsConstructor
@Tag(name = "Admin Badges")
public class AdminBadgeController {

	private final AdminBadgeService service;

	@GetMapping
	@Operation(summary = "Whole badge catalog, including inactive badges, with earner counts")
	public ResponseEntity<List<AdminBadgeDTO>> list() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Badge catalog detail")
	public ResponseEntity<AdminBadgeDTO> get(@PathVariable Long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Edit name/description/imageKey/sortOrder/rewards or switch isActive; version required")
	public ResponseEntity<AdminBadgeDTO> patch(@PathVariable Long id, @RequestBody JsonNode body) {
		return ResponseEntity.ok(service.patch(id, body));
	}

	@PostMapping("/assets/upload-url")
	@Operation(summary = "Presigned PUT for a PNG/WebP badge image; send the returned objectName as imageKey")
	public ResponseEntity<PresignedUploadResponse> imageUploadUrl(@RequestBody UploadUrlRequest request) {
		return ResponseEntity.ok(service.imageUploadUrl(request.fileName()));
	}

	@GetMapping("/{id}/refill-estimate")
	@Operation(summary = "Activated accounts that qualify but do not hold the badge yet (read-only, PD-BDG-13d)")
	public ResponseEntity<RefillEstimateDTO> refillEstimate(@PathVariable Long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.refillEstimate(id));
	}
}
