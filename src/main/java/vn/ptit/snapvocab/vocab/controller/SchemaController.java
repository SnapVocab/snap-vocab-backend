package vn.ptit.snapvocab.vocab.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.vocab.domain.dto.template.CreateTemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.MyTemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.vocab.service.SchemaService;
import vn.ptit.snapvocab.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schemas")
public class SchemaController {

	private final SchemaService schemaService;
	private final UserService userService;

	@PostMapping
	public ResponseEntity<SchemaDTO> createSchema(@Valid @RequestBody CreateSchemaDTO createSchemaDTO) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.createSchema(createSchemaDTO, currentUserId));
	}

	@GetMapping
	public ResponseEntity<List<SchemaDTO>> getAllSchemas() {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(schemaService.getAllSchemas(currentUserId));
	}

	@GetMapping("/{schemaId}")
	public ResponseEntity<SchemaDTO> getSchemaById(@PathVariable Long schemaId) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(schemaService.getSchemaById(schemaId, currentUserId));
	}

	@PutMapping("/{schemaId}")
	public ResponseEntity<SchemaDTO> updateSchema(
			@PathVariable Long schemaId,
			@Valid @RequestBody CreateSchemaDTO createSchemaDTO
	) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(schemaService.updateSchema(schemaId, createSchemaDTO, currentUserId));
	}

	@PostMapping("/{schemaId}/templates")
	public ResponseEntity<TemplateDTO> createTemplate(
			@PathVariable Long schemaId,
			@Valid @RequestBody CreateTemplateDTO createTemplateDTO
	) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(schemaService.createTemplate(schemaId, createTemplateDTO, currentUserId));
	}

	/** "Mẫu thẻ của tôi": templates the learner built, with how many topics show each. */
	@GetMapping("/templates/mine")
	public ResponseEntity<List<MyTemplateDTO>> getMyTemplates() {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(schemaService.getMyTemplates(currentUserId));
	}

	/** "Cập nhật mẫu này": rewrites the learner's own template; every topic showing it changes. */
	@PutMapping("/{schemaId}/templates/{templateId}")
	public ResponseEntity<TemplateDTO> updateTemplate(
			@PathVariable Long schemaId,
			@PathVariable Long templateId,
			@Valid @RequestBody CreateTemplateDTO createTemplateDTO
	) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(schemaService.updateTemplate(schemaId, templateId, createTemplateDTO, currentUserId));
	}

	/** Only a template no topic shows; otherwise 409 TEMPLATE_IN_USE. */
	@DeleteMapping("/{schemaId}/templates/{templateId}")
	public ResponseEntity<Void> deleteTemplate(@PathVariable Long schemaId, @PathVariable Long templateId) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		schemaService.deleteTemplate(schemaId, templateId, currentUserId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{schemaId}")
	public ResponseEntity<Void> deleteSchema(@PathVariable Long schemaId) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		schemaService.deleteSchema(schemaId, currentUserId);
		return ResponseEntity.noContent().build();
	}
}

