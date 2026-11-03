package vn.ptit.snapvocab.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.audit.domain.dto.AuditLogResponseDTO;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditArea;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditKind;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditQueryService;

/** Read-only by design: no API writes, edits or deletes audit rows. ROLE_ADMIN via SecurityConfiguration. */
@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
@Tag(name = "Admin Audit Log")
public class AdminAuditController {

	private final AdminAuditQueryService service;

	@GetMapping
	@Operation(summary = "Admin writes of a period, newest first, with the period summary (activity_log_ui.md)")
	public ResponseEntity<AuditLogResponseDTO> list(@RequestParam(defaultValue = "30") int days,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
			@RequestParam(required = false) AuditArea area, @RequestParam(required = false) String actor,
			@RequestParam(required = false) AuditKind kind, @RequestParam(required = false) AuditTargetType targetType,
			@RequestParam(required = false) String targetId, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return ResponseEntity.ok(service.list(days, day, area, actor, kind, targetType, targetId, q, page, size));
	}
}
