package vn.ptit.snapvocab.adminstats.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO;
import vn.ptit.snapvocab.adminstats.service.AdminAnalyticsService;

/** ROLE_ADMIN via SecurityConfiguration (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics")
public class AdminAnalyticsController {

	private final AdminAnalyticsService service;

	@GetMapping
	@Operation(summary = "Learning, retention, economy and scan trends over 7, 30 or 90 days (admin_analytics_backend.md)")
	public ResponseEntity<AdminAnalyticsDTO> analytics(@RequestParam(defaultValue = "30") int days) {
		return ResponseEntity.ok(service.analytics(days));
	}
}
