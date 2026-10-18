package vn.ptit.snapvocab.xp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.xp.domain.dto.XpOverviewDTO;
import vn.ptit.snapvocab.xp.service.XpService;

@RestController
@RequestMapping("/api/gamification")
@RequiredArgsConstructor
@Tag(name = "Gamification")
public class XpController {

	private final XpService xpService;

	@GetMapping("/xp")
	@Operation(summary = "Total XP, current level with progress to the next, and recent XP events (MH-STATS-02)")
	public ResponseEntity<XpOverviewDTO> xp() {
		Long userId = SecurityUtils.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
		return ResponseEntity.ok(xpService.getOverview(userId));
	}
}
