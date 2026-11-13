package vn.ptit.snapvocab.leaderboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWeek;
import vn.ptit.snapvocab.leaderboard.domain.dto.AdminLeaderboardDTO;
import vn.ptit.snapvocab.leaderboard.service.LeaderboardService;

/** Read-only (LB-12); /api/admin/** already requires ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/leaderboards")
@RequiredArgsConstructor
@Tag(name = "Admin Leaderboard")
public class AdminLeaderboardController {

	private final LeaderboardService leaderboardService;

	@GetMapping
	@Operation(summary = "Whole weekly board of the snapshot learners see, with raw XP signals (leaderboard_backend.md §7)")
	public ResponseEntity<AdminLeaderboardDTO> get(@RequestParam(defaultValue = "CURRENT") LeaderboardWeek week) {
		return ResponseEntity.ok(leaderboardService.forAdmin(week));
	}
}
