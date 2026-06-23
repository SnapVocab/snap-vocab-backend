package vn.ptit.snapvocab.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.domain.dto.RequestLoginDTO;
import vn.ptit.snapvocab.domain.dto.ResponseLoginDTO;
import vn.ptit.snapvocab.service.AuthenticationService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

	private final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);

	private final AuthenticationService authenticationService;

	@PostMapping("/login")
	public ResponseEntity<ResponseLoginDTO> login(
			@Valid @RequestBody RequestLoginDTO requestLoginDTO) {
		LOG.info("POST /auth/login email={}", requestLoginDTO.getEmail());

		return ResponseEntity.ok(this.authenticationService.login(requestLoginDTO));
	}
}
