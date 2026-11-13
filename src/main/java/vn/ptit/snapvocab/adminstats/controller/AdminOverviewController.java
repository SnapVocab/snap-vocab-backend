package vn.ptit.snapvocab.adminstats.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO;
import vn.ptit.snapvocab.adminstats.service.AdminOverviewService;

/** ROLE_ADMIN via SecurityConfiguration (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
@Tag(name = "Admin Overview")
public class AdminOverviewController {

	private final AdminOverviewService service;

	@GetMapping
	@Operation(summary = "Dashboard numbers: learners, scan, labels, Coin, missions, sales (admin_overview_backend.md)")
	public ResponseEntity<AdminOverviewDTO> overview() {
		return ResponseEntity.ok(service.overview());
	}
}
