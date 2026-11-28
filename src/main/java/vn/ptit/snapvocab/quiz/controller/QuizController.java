package vn.ptit.snapvocab.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.quiz.domain.dto.CheckQuestionRequest;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuestionCheckDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizHistoryItemDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizLifecycleDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizPlayDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizResultDTO;
import vn.ptit.snapvocab.quiz.domain.dto.CreateQuizRequest;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptRequest;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;
import vn.ptit.snapvocab.quiz.service.QuizService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Quiz")
public class QuizController {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final QuizService quizService;

	@PostMapping("/me/quizzes")
	@Operation(summary = "Create a quiz session from a topic")
	public ResponseEntity<QuizPlayDTO> create(@Valid @RequestBody CreateQuizRequest request,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
		return ResponseEntity.status(HttpStatus.CREATED).body(quizService.create(request, idempotencyKey));
	}

	@GetMapping("/me/quizzes/{quizId}")
	@Operation(summary = "Get a quiz session as played (never includes the answer key)")
	public ResponseEntity<QuizPlayDTO> get(@PathVariable Long quizId) {
		return ResponseEntity.ok(quizService.getQuiz(quizId));
	}

	@PostMapping("/questions/{questionId}/check")
	@Operation(summary = "Grade one MCQ or LISTENING ({optionKey}) or FILL_BLANK ({answer}) question immediately")
	public ResponseEntity<QuestionCheckDTO> check(@PathVariable Long questionId,
			@RequestBody CheckQuestionRequest request,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
		return ResponseEntity.ok(quizService.checkQuestion(questionId, request, idempotencyKey));
	}

	@PostMapping("/me/quizzes/{quizId}/rounds/{roundNo}/attempts")
	@Operation(summary = "Grade one matching pair immediately")
	public ResponseEntity<MatchingAttemptDTO> attempt(@PathVariable Long quizId, @PathVariable int roundNo,
			@Valid @RequestBody MatchingAttemptRequest request,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
		return ResponseEntity.ok(quizService.attemptPair(quizId, roundNo, request, idempotencyKey));
	}

	@PostMapping("/me/quizzes/{quizId}/complete")
	@Operation(summary = "Finalize a fully graded session")
	public ResponseEntity<QuizResultDTO> complete(@PathVariable Long quizId) {
		return ResponseEntity.ok(quizService.complete(quizId));
	}

	@PostMapping("/me/quizzes/{quizId}/cancel")
	@Operation(summary = "Cancel an in-progress session")
	public ResponseEntity<QuizLifecycleDTO> cancel(@PathVariable Long quizId) {
		return ResponseEntity.ok(quizService.cancel(quizId));
	}

	@GetMapping("/me/quizzes/{quizId}/result")
	@Operation(summary = "Result of a finished session")
	public ResponseEntity<QuizResultDTO> result(@PathVariable Long quizId) {
		return ResponseEntity.ok(quizService.getResult(quizId));
	}

	@GetMapping("/me/quizzes")
	@Operation(summary = "Quiz history, newest first; optional mode/status filters")
	public ResponseEntity<PaginationObject<List<QuizHistoryItemDTO>>> history(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) QuizMode mode, @RequestParam(required = false) QuizStatus status) {
		return ResponseEntity.ok(quizService.getHistory(page, size, mode, status));
	}
}

