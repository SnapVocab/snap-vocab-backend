package vn.ptit.snapvocab.mission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.mission.domain.dto.DailyMissionsDTO;
import vn.ptit.snapvocab.mission.domain.dto.MissionClaimResultDTO;
import vn.ptit.snapvocab.mission.service.DailyMissionService;
import vn.ptit.snapvocab.security.SecurityUtils;

/** Learner Daily Mission API (daily_mission.md §8). The owner is always the JWT subject. */
@RestController
@RequestMapping("/api/me/daily-missions")
@RequiredArgsConstructor
@Tag(name = "Daily Mission")
public class DailyMissionController {

	private final DailyMissionService dailyMissionService;

	@GetMapping
	@Operation(summary = "Today's five missions (Asia/Ho_Chi_Minh), assigned on first call")
	public ResponseEntity<DailyMissionsDTO> today() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dailyMissionService.getToday(currentUserId()));
	}

	@PostMapping("/{id}/claim")
	@Operation(summary = "Claim one completed mission of today; replay-safe via Idempotency-Key")
	public ResponseEntity<MissionClaimResultDTO> claim(@PathVariable Long id,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return ResponseEntity.ok(dailyMissionService.claim(currentUserId(), id, idempotencyKey));
	}

	private static Long currentUserId() {
		return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
