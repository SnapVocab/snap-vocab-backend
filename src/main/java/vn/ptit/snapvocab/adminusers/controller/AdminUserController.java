package vn.ptit.snapvocab.adminusers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Detail;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.ListResponse;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.ReasonRequest;
import vn.ptit.snapvocab.adminusers.service.AdminUserService;

/** Admin › Người học (snapAdmin domains/admin-learners/types.ts). ROLE_ADMIN via SecurityConfiguration. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Learners")
public class AdminUserController {

	private final AdminUserService service;

	@GetMapping
	@Operation(summary = "Learners, filtered and paged, with population counts")
	public ResponseEntity<ListResponse> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String status, @RequestParam(required = false) String activity,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(service.list(q, status, activity, sort, page, size));
	}

	@GetMapping("/{id}")
	@Operation(summary = "One learner's profile: progress, activity, missions, badges, wallet, scans")
	public ResponseEntity<Detail> get(@PathVariable Long id) {
		return ResponseEntity.ok(service.detail(id));
	}

	@PostMapping("/{id}/ban")
	@Operation(summary = "Lock the account (reason 10..500 required); audited")
	public ResponseEntity<Detail> ban(@PathVariable Long id, @RequestBody(required = false) ReasonRequest body) {
		return ResponseEntity.ok(service.ban(id, body == null ? null : body.reason()));
	}

	@PostMapping("/{id}/unban")
	@Operation(summary = "Unlock the account (reason optional); audited")
	public ResponseEntity<Detail> unban(@PathVariable Long id, @RequestBody(required = false) ReasonRequest body) {
		return ResponseEntity.ok(service.unban(id, body == null ? null : body.reason()));
	}

	@PostMapping("/{id}/password-reset")
	@Operation(summary = "Send the app's password-reset email; audited")
	public ResponseEntity<Void> passwordReset(@PathVariable Long id) {
		service.sendPasswordReset(id);
		return ResponseEntity.noContent().build();
	}
}
