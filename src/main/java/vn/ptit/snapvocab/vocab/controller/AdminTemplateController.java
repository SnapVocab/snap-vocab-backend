package vn.ptit.snapvocab.vocab.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.UpdateTemplateRequest;
import vn.ptit.snapvocab.vocab.service.AdminTemplateService;

/** Admin › Mẫu thẻ writes (snapAdmin templates_ui.md §8). Reads stay on GET /api/schemas. ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/templates")
@RequiredArgsConstructor
@Tag(name = "Admin Card Templates")
public class AdminTemplateController {

	private final AdminTemplateService service;

	@PutMapping("/{id}")
	@Operation(summary = "Replace a system template's visible elements; version required; Quiz bindings checked; audited")
	public ResponseEntity<TemplateDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateTemplateRequest body) {
		return ResponseEntity.ok(service.update(id, body));
	}

	@PostMapping("/{id}/default")
	@Operation(summary = "Make this the schema's default template (what the app draws); version required; audited")
	public ResponseEntity<TemplateDTO> makeDefault(@PathVariable Long id,
			@Valid @RequestBody UpdateTemplateRequest.DefaultRequest body) {
		return ResponseEntity.ok(service.makeDefault(id, body.version()));
	}
}
