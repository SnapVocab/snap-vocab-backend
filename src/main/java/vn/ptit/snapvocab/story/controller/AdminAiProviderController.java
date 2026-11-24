package vn.ptit.snapvocab.story.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import vn.ptit.snapvocab.story.domain.dto.AiModelOptionDTO;
import vn.ptit.snapvocab.story.domain.dto.AiProviderDTO;
import vn.ptit.snapvocab.story.domain.dto.FetchModelsRequest;
import vn.ptit.snapvocab.story.domain.dto.TestAiProviderResponse;
import vn.ptit.snapvocab.story.domain.dto.UpsertAiProviderRequest;
import vn.ptit.snapvocab.story.service.AdminAiProviderService;

/** ROLE_ADMIN is enforced for /api/admin/** by SecurityConfiguration. */
@RestController
@RequestMapping("/api/admin/ai-providers")
@RequiredArgsConstructor
@Tag(name = "Admin AI Providers")
public class AdminAiProviderController {

	private final AdminAiProviderService service;

	@GetMapping
	@Operation(summary = "List all configured AI LLM providers ordered by fallback priority")
	public ResponseEntity<List<AiProviderDTO>> list() {
		return ResponseEntity.ok(service.listAll());
	}

	@PostMapping
	@Operation(summary = "Add a new AI LLM provider")
	public ResponseEntity<AiProviderDTO> create(@Valid @RequestBody UpsertAiProviderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update an existing AI LLM provider")
	public ResponseEntity<AiProviderDTO> update(
			@PathVariable Long id,
			@Valid @RequestBody UpsertAiProviderRequest request
	) {
		return ResponseEntity.ok(service.update(id, request));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete an AI LLM provider")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/models")
	@Operation(summary = "Dynamically fetch available models from a provider API")
	public ResponseEntity<List<AiModelOptionDTO>> fetchModels(@RequestBody FetchModelsRequest request) {
		return ResponseEntity.ok(service.fetchModels(request));
	}

	@PostMapping("/{id}/test")
	@Operation(summary = "Test connectivity and JSON generation for a saved AI provider")
	public ResponseEntity<TestAiProviderResponse> test(@PathVariable Long id) {
		return ResponseEntity.ok(service.test(id));
	}
}

