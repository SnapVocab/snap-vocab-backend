package vn.ptit.snapvocab.mission.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.mission.domain.dto.MissionTemplateDTO;
import vn.ptit.snapvocab.mission.service.AdminMissionTemplateService;

/** daily_mission.md §8.3. ROLE_ADMIN is enforced for /api/admin/** by SecurityConfiguration. No PUT, no DELETE. */
@RestController
@RequestMapping("/api/admin/mission-templates")
@RequiredArgsConstructor
@Tag(name = "Admin Mission Templates")
public class AdminMissionTemplateController {

	private final AdminMissionTemplateService service;

	@GetMapping
	@Operation(summary = "Whole catalog, optionally filtered by active/category (not paginated)")
	public ResponseEntity<List<MissionTemplateDTO>> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String category) {
		return ResponseEntity.ok(service.list(active, category));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Template detail")
	public ResponseEntity<MissionTemplateDTO> get(@PathVariable Long id) {
		return ResponseEntity.ok(service.get(id));
	}

	@PostMapping
	@Operation(summary = "Create an inactive template")
	public ResponseEntity<MissionTemplateDTO> create(@RequestBody JsonNode body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Partial update with required version; isActive switches the template on/off")
	public ResponseEntity<MissionTemplateDTO> patch(@PathVariable Long id, @RequestBody JsonNode body) {
		return ResponseEntity.ok(service.patch(id, body));
	}
}
