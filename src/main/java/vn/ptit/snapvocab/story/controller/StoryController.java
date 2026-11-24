package vn.ptit.snapvocab.story.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.story.domain.dto.GenerateStoryRequest;
import vn.ptit.snapvocab.story.domain.dto.StoryDTO;
import vn.ptit.snapvocab.story.domain.dto.StoryListResponse;
import vn.ptit.snapvocab.story.domain.dto.SubmitStoryClozeRequest;
import vn.ptit.snapvocab.story.domain.dto.SubmitStoryClozeResponse;
import vn.ptit.snapvocab.story.service.StoryService;
import vn.ptit.snapvocab.user.service.UserService;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "AI Context Stories")
public class StoryController {

	private final StoryService storyService;
	private final UserService userService;

	@PostMapping("/generate")
	@Operation(summary = "Generate a new AI Context Story (Blended Macaronic or Full English) from 3-20 words")
	public ResponseEntity<StoryDTO> generate(@Valid @RequestBody GenerateStoryRequest request) {
		Long userId = requireCurrentUserId();
		return ResponseEntity.status(HttpStatus.CREATED).body(storyService.generateStory(userId, request));
	}

	@GetMapping
	@Operation(summary = "List generated stories for the current user along with daily quota")
	public ResponseEntity<StoryListResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		Long userId = requireCurrentUserId();
		return ResponseEntity.ok(storyService.listStories(userId, page, size));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get detail of a generated story")
	public ResponseEntity<StoryDTO> get(@PathVariable Long id) {
		Long userId = requireCurrentUserId();
		return ResponseEntity.ok(storyService.getStory(userId, id));
	}

	@PostMapping("/{id}/submit")
	@Operation(summary = "Submit Cloze practice answers and earn XP")
	public ResponseEntity<SubmitStoryClozeResponse> submit(
			@PathVariable Long id,
			@Valid @RequestBody SubmitStoryClozeRequest request
	) {
		Long userId = requireCurrentUserId();
		return ResponseEntity.ok(storyService.submitCloze(userId, id, request));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a generated story from history")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		Long userId = requireCurrentUserId();
		storyService.deleteStory(userId, id);
		return ResponseEntity.noContent().build();
	}

	private Long requireCurrentUserId() {
		return userService.getCurrentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
	}
}

