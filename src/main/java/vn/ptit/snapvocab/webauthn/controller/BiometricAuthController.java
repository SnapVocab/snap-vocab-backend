package vn.ptit.snapvocab.webauthn.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.webauthn.service.BiometricAuthService;
import vn.ptit.snapvocab.webauthn.service.BiometricAuthService.BiometricRegisterResponse;

@RestController
@RequestMapping("/api/auth/biometric")
@RequiredArgsConstructor
public class BiometricAuthController {

	private static final Logger LOG = LoggerFactory.getLogger(BiometricAuthController.class);

	private final BiometricAuthService biometricAuthService;

	public record BiometricRegisterRequest(
			@Size(max = 120, message = "Device name must not exceed 120 characters")
			String deviceName
	) {}

	public record BiometricLoginRequest(
			@NotBlank(message = "credentialId must not be blank")
			String credentialId,

			@NotBlank(message = "biometricToken must not be blank")
			String biometricToken
	) {}

	@PostMapping("/register")
	public ResponseEntity<BiometricRegisterResponse> register(
			@Valid @RequestBody(required = false) BiometricRegisterRequest request) {
		LOG.info("POST /api/auth/biometric/register");
		String email = SecurityUtils.getCurrentUserLogin()
				.orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
		String deviceName = request != null ? request.deviceName() : null;
		return ResponseEntity.ok(biometricAuthService.registerDevice(email, deviceName));
	}

	@PostMapping("/login")
	public ResponseEntity<ResponseLoginDTO> login(@Valid @RequestBody BiometricLoginRequest request) {
		LOG.info("POST /api/auth/biometric/login");
		return ResponseEntity.ok(
				biometricAuthService.loginWithBiometric(request.credentialId(), request.biometricToken()));
	}
}

