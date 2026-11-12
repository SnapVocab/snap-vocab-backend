package vn.ptit.snapvocab.leaderboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWeek;
import vn.ptit.snapvocab.leaderboard.domain.dto.LeaderboardDTO;
import vn.ptit.snapvocab.leaderboard.service.LeaderboardService;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;

@RestController
@RequestMapping("/api/leaderboards")
@RequiredArgsConstructor
@Tag(name = "Gamification")
public class LeaderboardController {

	private final LeaderboardService leaderboardService;

	@GetMapping
	@Operation(summary = "Weekly XP leaderboard: top rows, my rank, gap and trend from one snapshot (MH-GAME-01)")
	public ResponseEntity<LeaderboardDTO> get(@RequestParam(defaultValue = "CURRENT") LeaderboardWeek week,
			@RequestParam(defaultValue = "50") int limit) {
		Long userId = SecurityUtils.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
		boolean eligible = !SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		return ResponseEntity.ok(leaderboardService.forLearner(userId, eligible, week, limit));
	}
}
