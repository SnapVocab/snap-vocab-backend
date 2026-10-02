package vn.ptit.snapvocab.vocab.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.vocab.domain.dto.collection.CollectionDTO;
import vn.ptit.snapvocab.vocab.domain.dto.collection.CreateCollectionDTO;
import vn.ptit.snapvocab.vocab.domain.dto.collection.UpdateCollectionDTO;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.service.CollectionService;
import vn.ptit.snapvocab.vocab.service.TopicService;
import vn.ptit.snapvocab.user.service.UserService;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

	private final CollectionService collectionService;
	private final TopicService topicService;
	private final UserService userService;

	@GetMapping
	public ResponseEntity<List<CollectionDTO>> getCollections(
			@RequestParam(required = false) CollectionType type) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		if (type == CollectionType.SYSTEM) {
			return ResponseEntity.ok(this.collectionService.getSystemCollections());
		}
		if (type == CollectionType.USER) {
			return ResponseEntity.ok(this.collectionService.getMyCollections(currentUserId));
		}
		return ResponseEntity.ok(this.collectionService.getAllCollections(currentUserId));
	}

	@GetMapping("/system")
	public ResponseEntity<List<CollectionDTO>> getSystemCollections() {
		return ResponseEntity.ok(this.collectionService.getSystemCollections());
	}

	@GetMapping("/public")
	public ResponseEntity<List<CollectionDTO>> getPublicCollections() {
		return ResponseEntity.ok(this.collectionService.getSystemCollections());
	}

	@GetMapping("/my")
	public ResponseEntity<List<CollectionDTO>> getMyCollections() {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(this.collectionService.getMyCollections(currentUserId));
	}

	@GetMapping("/{collectionId:[0-9]+}")
	public ResponseEntity<CollectionDTO> getCollectionById(@PathVariable Long collectionId) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(this.collectionService.getCollectionById(collectionId, currentUserId));
	}

	@PostMapping
	public ResponseEntity<CollectionDTO> createCollection(
			@Valid @RequestBody CreateCollectionDTO createCollectionDTO) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(
				this.collectionService.createCollection(createCollectionDTO, currentUserId));
	}

	@PutMapping("/{collectionId:[0-9]+}")
	public ResponseEntity<CollectionDTO> updateCollection(
			@PathVariable Long collectionId,
			@Valid @RequestBody UpdateCollectionDTO updateCollectionDTO) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		return ResponseEntity.ok(
				this.collectionService.updateCollection(collectionId, updateCollectionDTO, currentUserId));
	}

	@DeleteMapping("/{collectionId:[0-9]+}")
	public ResponseEntity<Void> deleteCollection(@PathVariable Long collectionId) {
		Long currentUserId = userService.getCurrentUserId().orElse(null);
		this.collectionService.deleteCollection(collectionId, currentUserId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{collectionId:[0-9]+}/topics")
	public ResponseEntity<PaginationObject<List<TopicDTO>>> getTopicsFromCollection(@PathVariable Long collectionId,
			@RequestParam(required = false) Long parentId, Pageable pageable) {
		return ResponseEntity.ok(
				this.topicService.getTopicsByCollection(collectionId, parentId, pageable));
	}

	@PostMapping("/{collectionId:[0-9]+}/topics")
	public ResponseEntity<TopicDTO> createCollectionTopic(
			@PathVariable Long collectionId,
			@Valid @RequestBody CreateTopicDTO createTopicDTO) {
		return ResponseEntity.ok(this.topicService.createCollectionTopic(collectionId, createTopicDTO));
	}
}

