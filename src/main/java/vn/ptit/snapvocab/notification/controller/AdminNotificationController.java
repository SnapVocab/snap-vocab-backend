package vn.ptit.snapvocab.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.notification.domain.dto.SystemNotificationRequest;
import vn.ptit.snapvocab.notification.domain.dto.SystemNotificationResultDTO;
import vn.ptit.snapvocab.notification.service.NotificationService;

/** notification.md §4.3 A8, no UI (OQ-NOTIF-03). ROLE_ADMIN is enforced for /api/admin/** by SecurityConfiguration. */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications")
public class AdminNotificationController {

	private final NotificationService notificationService;

	@PostMapping
	@Operation(summary = "In-app SYSTEM message to every activated user; dedupeKey makes it one-shot")
	public ResponseEntity<SystemNotificationResultDTO> create(@Valid @RequestBody SystemNotificationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createSystem(request));
	}
}
