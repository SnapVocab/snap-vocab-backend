package vn.ptit.snapvocab.webauthn.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnCredentialResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsRequest;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginVerifyRequest;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterVerifyRequest;
import vn.ptit.snapvocab.webauthn.service.WebAuthnAuthenticationService;
import vn.ptit.snapvocab.webauthn.service.WebAuthnRegistrationService;

@RestController
@RequestMapping("/api/auth/webauthn")
@RequiredArgsConstructor
public class WebAuthnController {

	private static final Logger LOG = LoggerFactory.getLogger(WebAuthnController.class);

	private final WebAuthnRegistrationService registrationService;
	private final WebAuthnAuthenticationService authenticationService;

	@PostMapping("/register/options")
	public ResponseEntity<WebAuthnRegisterOptionsResponse> registerOptions() {
		LOG.info("POST /api/auth/webauthn/register/options");
		String email = requireCurrentUserEmail();
		return ResponseEntity.ok(registrationService.createRegistrationOptions(email));
	}

	@PostMapping("/register/verify")
	public ResponseEntity<WebAuthnCredentialResponse> registerVerify(
			@Valid @RequestBody WebAuthnRegisterVerifyRequest request) {
		LOG.info("POST /api/auth/webauthn/register/verify");
		String email = requireCurrentUserEmail();
		return ResponseEntity.ok(registrationService.verifyRegistration(email, request));
	}

	@PostMapping("/login/options")
	public ResponseEntity<WebAuthnLoginOptionsResponse> loginOptions(
			@RequestBody(required = false) WebAuthnLoginOptionsRequest request) {
		LOG.info("POST /api/auth/webauthn/login/options");
		return ResponseEntity.ok(authenticationService.createLoginOptions(request));
	}

	@PostMapping("/login/verify")
	public ResponseEntity<ResponseLoginDTO> loginVerify(
			@Valid @RequestBody WebAuthnLoginVerifyRequest request) {
		LOG.info("POST /api/auth/webauthn/login/verify");
		return ResponseEntity.ok(authenticationService.verifyLogin(request));
	}

	@GetMapping("/credentials")
	public ResponseEntity<List<WebAuthnCredentialResponse>> listCredentials() {
		LOG.info("GET /api/auth/webauthn/credentials");
		String email = requireCurrentUserEmail();
		return ResponseEntity.ok(registrationService.listCredentials(email));
	}

	@DeleteMapping("/credentials/{id}")
	public ResponseEntity<Void> deleteCredential(@PathVariable Long id) {
		LOG.info("DELETE /api/auth/webauthn/credentials/{}", id);
		String email = requireCurrentUserEmail();
		registrationService.deleteCredential(email, id);
		return ResponseEntity.noContent().build();
	}

	private String requireCurrentUserEmail() {
		return SecurityUtils.getCurrentUserLogin()
				.orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
	}
}

