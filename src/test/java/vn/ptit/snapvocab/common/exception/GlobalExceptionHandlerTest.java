package vn.ptit.snapvocab.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionErrorCode;
import vn.ptit.snapvocab.mission.exception.MissionException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void unexpectedErrorsNeverLeakTheirMessage() {
		var response = handler.handleRuntime(new IllegalStateException("SELECT * FROM users -- secret"),
				new MockHttpServletRequest("GET", "/api/x"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void duplicateKeysKeepTheirConcurrentModificationMapping() {
		var cause = new SQLIntegrityConstraintViolationException("Duplicate entry 'x'", "23000", 1062);
		var response = handler.handleDataIntegrity(new DataIntegrityViolationException("x", cause),
				new MockHttpServletRequest("POST", "/api/x"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().getError()).isEqualTo("CONCURRENT_MODIFICATION");
	}

	@Test
	void otherConstraintViolationsAreServerErrorsNotRetryableConflicts() {
		// 3819 = CHECK constraint violated (the CARD_FRAME incident), same SQLState as a duplicate.
		var cause = new SQLIntegrityConstraintViolationException("Check constraint violated", "23000", 3819);
		var response = handler.handleDataIntegrity(new DataIntegrityViolationException("x", cause),
				new MockHttpServletRequest("POST", "/api/x"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void missionErrorsUseTheirStableCodeAndStatus() {
		var response = handler.handleMission(new MissionException(MissionErrorCode.MISSION_POOL_INSUFFICIENT, "x"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().getError()).isEqualTo("MISSION_POOL_INSUFFICIENT");
	}
}
