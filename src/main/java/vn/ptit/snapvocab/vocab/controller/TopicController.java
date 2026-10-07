package vn.ptit.snapvocab.vocab.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.common.dto.CursorPaginationResponse;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicItemDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicSchemaDTO;
import vn.ptit.snapvocab.vocab.service.TopicService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topics")
public class TopicController {

	private final Logger LOG = LoggerFactory.getLogger(TopicController.class);

	private final TopicService topicService;

	@GetMapping("/{topicId}")
	public ResponseEntity<PaginationObject<List<TopicItemData>>> getTopicItems(
			@PathVariable Long topicId, Pageable pageable) {
		LOG.info("[GET: /{}/items] Fetching topic items for topicId: {}", topicId, topicId);
		return ResponseEntity.ok(topicService.getTopicItems(topicId, pageable));
	}

	@GetMapping("/{topicId}/schema")
	public ResponseEntity<TopicSchemaDTO> getTopicSchema(@PathVariable Long topicId) {
		LOG.info("[GET: /{}/schema] Fetching topic schema for topicId: {}", topicId, topicId);
		return ResponseEntity.ok(topicService.getTopicSchema(topicId));
	}

	@PostMapping("/{topicId}/schema")
	public ResponseEntity<TopicSchemaDTO> createTopicSchema(
			@PathVariable Long topicId,
			@Valid @RequestBody CreateTopicSchemaDTO createTopicSchemaDTO
	) {
		LOG.info("[POST: /{}/schema] Creating topic schema for topicId: {}", topicId, topicId);
		return ResponseEntity.ok(topicService.createTopicSchema(topicId, createTopicSchemaDTO));
	}

	@PutMapping("/{topicId}/schema/{schemaId}")
	public ResponseEntity<TopicSchemaDTO> assignSchemaToTopic(
			@PathVariable Long topicId,
			@PathVariable Long schemaId
	) {
		LOG.info("[PUT: /{}/schema/{}}] Assigning schema to topic for topicId: {}, schemaId: {}",
				topicId, schemaId, topicId, schemaId);
		return ResponseEntity.ok(topicService.assignSchemaToTopic(topicId, schemaId));
	}

	@PostMapping("/{topicId}/schema/fork")
	public ResponseEntity<TopicSchemaDTO> forkTopicSchema(@PathVariable Long topicId) {
		LOG.info("[POST: /{}/schema/fork] Forking topic schema for topicId: {}", topicId, topicId);
		return ResponseEntity.ok(topicService.forkTopicSchema(topicId));
	}

	@PutMapping("/{topicId}/active-template/{templateId}")
	public ResponseEntity<TopicDTO> setActiveTemplate(
			@PathVariable Long topicId,
			@PathVariable Long templateId
	) {
		LOG.info(
				"[PUT: /{}/active-template/{}}] Setting active template for topicId: {}, templateId: {}",
				topicId, templateId, topicId, templateId);
		return ResponseEntity.ok(topicService.setActiveTemplate(topicId, templateId));
	}

	@PostMapping("/{topicId}/items")
	public ResponseEntity<TopicItemData> addTopicItem(
			@PathVariable Long topicId,
			@Valid @RequestBody CreateTopicItemDTO createTopicItemDTO
	) {
		LOG.info("[POST: /{}/items] Adding topic item with topicId: {}, createTopicItemDTO: {}",
				topicId, topicId, createTopicItemDTO);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(topicService.addTopicItem(topicId, createTopicItemDTO));
	}

	@GetMapping("/{topicId}/cursor")
	public ResponseEntity<CursorPaginationResponse<List<TopicItemData>>> getTopicItemsByCursor(
			@PathVariable Long topicId, @RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") Long limit) {
		LOG.info(
				"[GET: /{}/cursor] Fetching topic items by cursor for topicId: {}, cursor: {}, limit: {}",
				topicId, topicId, cursor, limit);
		return ResponseEntity.ok(topicService.getTopicItemsByCursor(topicId, cursor, limit));
	}
}