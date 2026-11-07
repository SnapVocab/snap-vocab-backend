package vn.ptit.snapvocab.study.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import vn.ptit.snapvocab.study.service.CollectionStudyPlanner;
import vn.ptit.snapvocab.study.domain.dto.StudyCountsDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.study.domain.dto.ReviewResultDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyCollectionDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyQueueDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyTodayDTO;
import vn.ptit.snapvocab.study.domain.dto.SubmitRatingRequest;
import vn.ptit.snapvocab.study.service.StudyService;

/** The Learn tab (learn_ui.md): today's summary, the cross-collection session and the learner's library. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study")
public class TodayStudyController {
	private final StudyService studyService;
    private final CollectionStudyPlanner planner;

    @GetMapping("/counts")
    public ResponseEntity<List<StudyCountsDTO>> counts(@RequestParam(required = false) CollectionType type) {
        return ResponseEntity.ok(planner.allCounts(currentUserId(), type));
    }

	@GetMapping("/today")
	public ResponseEntity<StudyTodayDTO> getToday() {
		return ResponseEntity.ok(studyService.getToday(currentUserId()));
	}

	@GetMapping("/queue")
	public ResponseEntity<StudyQueueDTO> getQueue() {
		return ResponseEntity.ok(studyService.getTodayQueue(currentUserId()));
	}

	@GetMapping("/collections")
	public ResponseEntity<List<StudyCollectionDTO>> getLibrary() {
		return ResponseEntity.ok(studyService.getLibrary(currentUserId()));
	}

	@PostMapping("/items/{topicItemId}/reviews")
	public ResponseEntity<ReviewResultDTO> submitRating(@PathVariable Long topicItemId,
			@Valid @RequestBody SubmitRatingRequest request) {
		return ResponseEntity.ok(studyService.submitTodayRating(currentUserId(), topicItemId, request.rating()));
	}

	private Long currentUserId() {
		return SecurityUtils.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
