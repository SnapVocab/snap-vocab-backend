package vn.ptit.snapvocab.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.ptit.snapvocab.common.dto.RestResponse;
import vn.ptit.snapvocab.quiz.exception.QuizException;
import vn.ptit.snapvocab.scan.exception.ScanException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<RestResponse<Map<String, String>>> handleValidation(
			MethodArgumentNotValidException ex) {

		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));

		RestResponse<Map<String, String>> response = RestResponse.<Map<String, String>>builder()
				.statusCode(400)
				.error("Validation failed")
				.data(errors)
				.build();

		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<RestResponse<Object>> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(400)
				.error("Missing request parameter: " + ex.getParameterName())
				.build();

		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<RestResponse<Object>> handleAuthentication(AuthenticationException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(401)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
	}

	@ExceptionHandler(BadRequestAlertException.class)
	public ResponseEntity<RestResponse<Object>> handleBadRequest(BadRequestAlertException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(400)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(IdInvalidException.class)
	public ResponseEntity<RestResponse<Object>> handleIdInvalid(IdInvalidException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(400)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<RestResponse<Object>> handleNotFound(ResourceNotFoundException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(404)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
	}

	@ExceptionHandler(ResourceAlreadyExistException.class)
	public ResponseEntity<RestResponse<Object>> handleConflict(ResourceAlreadyExistException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(409)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
	}

	@ExceptionHandler(UserNotActivatedException.class)
	public ResponseEntity<RestResponse<Object>> handleUserNotActivated(UserNotActivatedException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(403)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	}

	@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
	public ResponseEntity<RestResponse<Object>> handleAccessDenied(
			org.springframework.security.access.AccessDeniedException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(403)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	}

	@ExceptionHandler(ScanException.class)
	public ResponseEntity<RestResponse<Object>> handleScan(ScanException ex) {

		log.warn("Scan failed with {}: {}", ex.getCode(), ex.getMessage());
		RestResponse<Object> response = RestResponse.builder()
				.statusCode(ex.getCode().getStatus().value())
				.error(ex.getCode().name())
				.data(ex.getDetails())
				.build();

		return ResponseEntity.status(ex.getCode().getStatus()).body(response);
	}

	@ExceptionHandler(QuizException.class)
	public ResponseEntity<RestResponse<Object>> handleQuiz(QuizException ex) {

		log.info("Quiz request rejected with {}: {}", ex.getCode(), ex.getMessage());
		RestResponse<Object> response = RestResponse.builder()
				.statusCode(ex.getCode().getStatus().value())
				.error(ex.getCode().name())
				.data(ex.getDetails())
				.build();

		return ResponseEntity.status(ex.getCode().getStatus()).body(response);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<RestResponse<Object>> handleRuntime(RuntimeException ex, HttpServletRequest request) {

		log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(500)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}
}

