package vn.ptit.snapvocab.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionErrorCode;
import vn.ptit.snapvocab.mission.exception.MissionException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void unexpectedErrorsNeverLeakTheirMessage() {
		var response = handler.handleRuntime(new IllegalStateException("SELECT * FROM users -- secret"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void constraintViolationsKeepTheirConcurrentModificationMapping() {
		var response = handler.handleDataIntegrity(new DataIntegrityViolationException("Duplicate entry 'x'"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().getError()).isEqualTo("CONCURRENT_MODIFICATION");
	}

	@Test
	void missionErrorsUseTheirStableCodeAndStatus() {
		var response = handler.handleMission(new MissionException(MissionErrorCode.MISSION_POOL_INSUFFICIENT, "x"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().getError()).isEqualTo("MISSION_POOL_INSUFFICIENT");
	}
}
