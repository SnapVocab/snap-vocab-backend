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
import org.springframework.web.bind.annotation.RestController;
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
		return ResponseEntity.ok(topicService.getTopicItems(topicId, pageable));
	}

	@GetMapping("/{topicId}/schema")
	public ResponseEntity<TopicSchemaDTO> getTopicSchema(@PathVariable Long topicId) {
		return ResponseEntity.ok(topicService.getTopicSchema(topicId));
	}

	@PostMapping("/{topicId}/schema")
	public ResponseEntity<TopicSchemaDTO> createTopicSchema(
			@PathVariable Long topicId,
			@Valid @RequestBody CreateTopicSchemaDTO createTopicSchemaDTO
	) {
		return ResponseEntity.ok(topicService.createTopicSchema(topicId, createTopicSchemaDTO));
	}

	@PutMapping("/{topicId}/schema/{schemaId}")
	public ResponseEntity<TopicSchemaDTO> assignSchemaToTopic(
			@PathVariable Long topicId,
			@PathVariable Long schemaId
	) {
		return ResponseEntity.ok(topicService.assignSchemaToTopic(topicId, schemaId));
	}

	@PostMapping("/{topicId}/schema/fork")
	public ResponseEntity<TopicSchemaDTO> forkTopicSchema(@PathVariable Long topicId) {
		return ResponseEntity.ok(topicService.forkTopicSchema(topicId));
	}

	@PutMapping("/{topicId}/active-template/{templateId}")
	public ResponseEntity<TopicDTO> setActiveTemplate(
			@PathVariable Long topicId,
			@PathVariable Long templateId
	) {
		return ResponseEntity.ok(topicService.setActiveTemplate(topicId, templateId));
	}

	@PostMapping("/{topicId}/items")
	public ResponseEntity<TopicItemData> addTopicItem(
			@PathVariable Long topicId,
			@Valid @RequestBody CreateTopicItemDTO createTopicItemDTO
	) {
		LOG.info("Adding topic item with topicId: {}, createTopicItemDTO: {}", topicId,
				createTopicItemDTO);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(topicService.addTopicItem(topicId, createTopicItemDTO));
	}
}
