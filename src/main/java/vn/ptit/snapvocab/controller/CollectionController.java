package vn.ptit.snapvocab.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.domain.dto.collection.CollectionDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.service.CollectionService;
import vn.ptit.snapvocab.service.TopicService;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

	private final CollectionService collectionService;
	private final TopicService topicService;

	@GetMapping
	public ResponseEntity<List<CollectionDTO>> getCollections() {
		return ResponseEntity.ok(this.collectionService.getAllCollections());
	}

	@GetMapping("/{collectionId}/topics")
	public ResponseEntity<List<TopicDTO>> getTopicsFromCollection(@PathVariable Long collectionId) {
		return ResponseEntity.ok(this.topicService.getTopicsByCollection(collectionId));
	}
}
