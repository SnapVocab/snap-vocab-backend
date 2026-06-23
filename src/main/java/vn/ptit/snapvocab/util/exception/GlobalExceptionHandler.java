package vn.ptit.snapvocab.util.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.ptit.snapvocab.domain.dto.RestResponse;

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

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<RestResponse<Object>> handleRuntime(RuntimeException ex) {

		RestResponse<Object> response = RestResponse.builder()
				.statusCode(500)
				.error(ex.getMessage())
				.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}
}
