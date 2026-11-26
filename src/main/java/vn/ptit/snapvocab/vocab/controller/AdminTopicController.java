package vn.ptit.snapvocab.vocab.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.audit.domain.dto.AuditReasonRequest;
import vn.ptit.snapvocab.vocab.domain.dto.topic.HiddenTopicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.UpdateTopicDTO;
import vn.ptit.snapvocab.vocab.service.TopicService;

/**
 * Admin › Chủ đề: edit and delete SYSTEM topics (FR-14.05). Create, items and template switch stay on the shared
 * /api/collections and /api/topics endpoints. ROLE_ADMIN via SecurityConfiguration.
 */
@RestController
@RequestMapping("/api/admin/topics")
@RequiredArgsConstructor
@Tag(name = "Admin Topics")
public class AdminTopicController {

	private final TopicService topicService;

	@PutMapping("/{id}")
	@Operation(summary = "Rename / re-describe a system topic; audited")
	public ResponseEntity<TopicDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateTopicDTO body) {
		return ResponseEntity.ok(topicService.updateSystemTopic(id, body));
	}

	@GetMapping("/hidden")
	@Operation(summary = "Hidden topics of a system collection, newest first, with learner counts")
	public ResponseEntity<List<HiddenTopicDTO>> hidden(@RequestParam Long collectionId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(topicService.hiddenSystemTopics(collectionId));
	}

	@PostMapping("/{id}/hide")
	@Operation(summary = "Soft delete: hide the topic and its subtopics; reviews of studied cards go on; reason required")
	public ResponseEntity<HiddenTopicDTO> hide(@PathVariable Long id, @RequestBody(required = false) AuditReasonRequest body) {
		return ResponseEntity.ok(topicService.hideSystemTopic(id, body == null ? null : body.reason()));
	}

	@PostMapping("/{id}/restore")
	@Operation(summary = "Bring back a hidden topic with the subtopics hidden with it")
	public ResponseEntity<TopicDTO> restore(@PathVariable Long id, @RequestBody(required = false) AuditReasonRequest body) {
		return ResponseEntity.ok(topicService.restoreSystemTopic(id, body == null ? null : body.reason()));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Purge a system topic without subtopics nobody studied (else 409); reason 10..500 required")
	public ResponseEntity<Void> delete(@PathVariable Long id, @RequestBody(required = false) AuditReasonRequest body) {
		topicService.deleteSystemTopic(id, body == null ? null : body.reason());
		return ResponseEntity.noContent().build();
	}
}
