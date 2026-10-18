package vn.ptit.snapvocab.badge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.badge.domain.dto.AdminBadgeDTO;
import vn.ptit.snapvocab.badge.service.AdminBadgeService;

/** badges.md §8.4, P1. SecurityConfiguration requires ROLE_ADMIN for /api/admin/**. No write endpoints. */
@RestController
@RequestMapping("/api/admin/badges")
@RequiredArgsConstructor
@Tag(name = "Admin Badges")
public class AdminBadgeController {

	private final AdminBadgeService service;

	@GetMapping
	@Operation(summary = "Whole badge catalog, including inactive badges, with earner counts (read-only)")
	public ResponseEntity<List<AdminBadgeDTO>> list() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Badge catalog detail (read-only)")
	public ResponseEntity<AdminBadgeDTO> get(@PathVariable Long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
	}
}
