package vn.ptit.snapvocab.study.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import vn.ptit.snapvocab.study.service.CollectionStudySettingsService;
import vn.ptit.snapvocab.study.service.CollectionStudyPlanner;
import vn.ptit.snapvocab.study.domain.dto.CollectionStudySettingsDTO;
import vn.ptit.snapvocab.study.domain.dto.UpdateCollectionStudySettingsRequest;
import vn.ptit.snapvocab.study.domain.dto.CollectionStudyOverviewDTO;
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
    private final CollectionStudySettingsService settingsService;
    private final CollectionStudyPlanner planner;

    @GetMapping("/settings")
    public ResponseEntity<CollectionStudySettingsDTO> getSettings(@PathVariable Long collectionId) {
        return ResponseEntity.ok(settingsService.get(currentUserId(), collectionId));
    }

    @PutMapping("/settings")
    public ResponseEntity<CollectionStudySettingsDTO> updateSettings(@PathVariable Long collectionId,
            @Valid @RequestBody UpdateCollectionStudySettingsRequest request) {
        return ResponseEntity.ok(settingsService.update(currentUserId(), collectionId, request));
    }

    @DeleteMapping("/settings")
    public ResponseEntity<Void> resetSettings(@PathVariable Long collectionId) {
        settingsService.reset(currentUserId(), collectionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/today")
    public ResponseEntity<CollectionStudyOverviewDTO> overview(@PathVariable Long collectionId) {
        return ResponseEntity.ok(planner.overview(currentUserId(), collectionId));
    }

	@GetMapping("/queue")
	public ResponseEntity<StudyQueueDTO> getQueue(@PathVariable Long collectionId) {
		return ResponseEntity.ok(studyService.getCollectionQueue(currentUserId(), collectionId));
	}

	@PostMapping("/items/{topicItemId}/reviews")
	public ResponseEntity<ReviewResultDTO> submitRating(@PathVariable Long collectionId,
			@PathVariable Long topicItemId, @Valid @RequestBody SubmitRatingRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return ResponseEntity.ok(studyService.submitCollectionRating(
				currentUserId(), collectionId, topicItemId, request.rating(), idempotencyKey));
	}

	private Long currentUserId() {
		return SecurityUtils.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
