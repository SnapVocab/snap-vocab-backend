package vn.ptit.snapvocab.controller;

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
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.service.SchemaService;
import vn.ptit.snapvocab.service.UserService;

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

	@DeleteMapping("/{schemaId}")
	public ResponseEntity<Void> deleteSchema(@PathVariable Long schemaId) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		schemaService.deleteSchema(schemaId, currentUserId);
		return ResponseEntity.noContent().build();
	}
}
