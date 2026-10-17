package vn.ptit.snapvocab.study.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.study.domain.dto.ReviewResultDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyQueueDTO;
import vn.ptit.snapvocab.study.domain.dto.SubmitRatingRequest;
import vn.ptit.snapvocab.study.service.StudyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/collections/{collectionId}/study")
public class CollectionStudyController {
	private final StudyService studyService;

	@GetMapping("/queue")
	public ResponseEntity<StudyQueueDTO> getQueue(@PathVariable Long collectionId) {
		return ResponseEntity.ok(studyService.getCollectionQueue(currentUserId(), collectionId));
	}

	@PostMapping("/items/{topicItemId}/reviews")
	public ResponseEntity<ReviewResultDTO> submitRating(@PathVariable Long collectionId,
			@PathVariable Long topicItemId, @Valid @RequestBody SubmitRatingRequest request) {
		return ResponseEntity.ok(studyService.submitCollectionRating(
				currentUserId(), collectionId, topicItemId, request.rating()));
	}

	private Long currentUserId() {
		return SecurityUtils.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
