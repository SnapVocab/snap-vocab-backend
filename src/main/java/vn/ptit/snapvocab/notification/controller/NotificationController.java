package vn.ptit.snapvocab.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.notification.domain.dto.NotificationPageDTO;
import vn.ptit.snapvocab.notification.domain.dto.NotificationReadDTO;
import vn.ptit.snapvocab.notification.domain.dto.ReadAllResultDTO;
import vn.ptit.snapvocab.notification.domain.dto.UnreadCountDTO;
import vn.ptit.snapvocab.notification.service.NotificationService;
import vn.ptit.snapvocab.security.SecurityUtils;

/** Learner inbox, MH-PROFILE-04 (notification.md §4.3 A1–A4). The owner is always the JWT subject. */
@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	@Operation(summary = "Inbox, newest first; pass the previous page's nextCursor as cursor")
	public ResponseEntity<NotificationPageDTO> list(@RequestParam(required = false) Long cursor,
			@RequestParam(required = false) Integer size) {
		return ResponseEntity.ok(notificationService.list(currentUserId(), cursor, size));
	}

	@GetMapping("/unread-count")
	@Operation(summary = "Exact unread count for the Home bell")
	public ResponseEntity<UnreadCountDTO> unreadCount() {
		return ResponseEntity.ok(notificationService.unreadCount(currentUserId()));
	}

	@PutMapping("/{id}/read")
	@Operation(summary = "Mark one notification read (idempotent)")
	public ResponseEntity<NotificationReadDTO> markRead(@PathVariable Long id) {
		return ResponseEntity.ok(notificationService.markRead(currentUserId(), id));
	}

	@PostMapping("/read-all")
	@Operation(summary = "Mark every unread notification read")
	public ResponseEntity<ReadAllResultDTO> markAllRead() {
		return ResponseEntity.ok(notificationService.markAllRead(currentUserId()));
	}

	static Long currentUserId() {
		return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
