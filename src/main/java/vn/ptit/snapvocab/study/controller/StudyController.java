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
@RequestMapping("/api/topics/{topicId}/study")
public class StudyController {

	private final StudyService studyService;

	@GetMapping("/queue")
	public ResponseEntity<StudyQueueDTO> getQueue(@PathVariable Long topicId) {
		return ResponseEntity.ok(studyService.getQueue(currentUserId(), topicId));
	}

	@PostMapping("/items/{topicItemId}/reviews")
	public ResponseEntity<ReviewResultDTO> submitRating(
			@PathVariable Long topicId,
			@PathVariable Long topicItemId,
			@Valid @RequestBody SubmitRatingRequest request) {
		return ResponseEntity.ok(studyService.submitRating(
				currentUserId(), topicId, topicItemId, request.rating()));
	}

	private Long currentUserId() {
		return SecurityUtils.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
