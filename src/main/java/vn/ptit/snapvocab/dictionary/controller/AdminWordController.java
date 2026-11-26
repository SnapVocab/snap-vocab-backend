package vn.ptit.snapvocab.dictionary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.audit.domain.dto.AuditReasonRequest;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.dictionary.domain.dto.AdminWordDTOs.DeletedWordDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.AdminWordDTOs.UpdateWordRequest;
import vn.ptit.snapvocab.dictionary.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.dictionary.service.AdminWordService;

/** Admin › Từ điển writes. Reads stay on /api/words (GET /api/words/{id} also opens a soft-deleted word). ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/words")
@RequiredArgsConstructor
@Tag(name = "Admin Dictionary")
public class AdminWordController {

	private final AdminWordService service;

	@GetMapping("/deleted")
	@Operation(summary = "Soft-deleted words, newest first, optionally by prefix")
	public ResponseEntity<PaginationObject<List<DeletedWordDTO>>> deleted(@RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.deleted(q, page, size));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace IPA, Vietnamese translations and meanings; version required; audited")
	public ResponseEntity<LookupResultDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateWordRequest body) {
		return ResponseEntity.ok(service.update(id, body));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Soft delete (reason optional, 10..500); audited")
	public ResponseEntity<LookupResultDTO> delete(@PathVariable Long id,
			@RequestBody(required = false) AuditReasonRequest body) {
		return ResponseEntity.ok(service.softDelete(id, body == null ? null : body.reason()));
	}

	@PostMapping("/{id}/restore")
	@Operation(summary = "Undo a soft delete; audited")
	public ResponseEntity<LookupResultDTO> restore(@PathVariable Long id,
			@RequestBody(required = false) AuditReasonRequest body) {
		return ResponseEntity.ok(service.restore(id, body == null ? null : body.reason()));
	}
}
