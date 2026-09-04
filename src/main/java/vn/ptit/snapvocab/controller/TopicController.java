package vn.ptit.snapvocab.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaData;
import vn.ptit.snapvocab.service.TopicService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topics")
public class TopicController {

	private final TopicService topicService;

	@GetMapping("/{topicId}")
	public ResponseEntity<PaginationObject<List<TopicItemData>>> getTopicItems(
			@PathVariable Long topicId, Pageable pageable) {
		topicService.validateTopicAccess(topicId);
		return ResponseEntity.ok(topicService.getTopicItems(topicId, pageable));
	}

	@GetMapping("/{topicId}/schema")
	public ResponseEntity<TopicSchemaData> getTopicSchema(@PathVariable Long topicId) {
		topicService.validateTopicAccess(topicId);
		return ResponseEntity.ok(topicService.getTopicSchema(topicId));
	}

}
