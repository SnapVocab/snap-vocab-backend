package vn.ptit.snapvocab.badge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.badge.domain.dto.BadgesDTO;
import vn.ptit.snapvocab.badge.domain.dto.SeenBadgesRequest;
import vn.ptit.snapvocab.badge.domain.dto.SeenBadgesResultDTO;
import vn.ptit.snapvocab.badge.service.BadgeService;
import vn.ptit.snapvocab.security.SecurityUtils;

/** Learner Badges API (badges.md §8). The owner is always the JWT subject. */
@RestController
@RequestMapping("/api/me/badges")
@RequiredArgsConstructor
@Tag(name = "Badges")
public class BadgeController {

	private final BadgeService badgeService;

	@GetMapping
	@Operation(summary = "Badge gallery with progress; read-only, never grants")
	public ResponseEntity<BadgesDTO> list() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(badgeService.getBadges(currentUserId()));
	}

	@PostMapping("/sync")
	@Operation(summary = "Grant every badge whose threshold is reached, then return the gallery")
	public ResponseEntity<BadgesDTO> sync() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(badgeService.sync(currentUserId()));
	}

	@PostMapping("/seen")
	@Operation(summary = "Mark earned badges as seen (1-50 ids); unknown or already seen ids are ignored")
	public ResponseEntity<SeenBadgesResultDTO> seen(@RequestBody SeenBadgesRequest request) {
		return ResponseEntity.ok(badgeService.markSeen(currentUserId(), request.badgeIds()));
	}

	private static Long currentUserId() {
		return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
