package vn.ptit.snapvocab.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.user.domain.dto.auth.ForgotPasswordRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.RefreshTokenRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.RequestLoginDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.RequestRegisterDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.ResendCodeRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.ResetPasswordRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.VerifyResetCodeRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseRegisterDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.VerifyMailRequest;
import vn.ptit.snapvocab.user.domain.dto.user.ChangePasswordRequest;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.user.service.AuthenticationService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

	private final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);

	private final AuthenticationService authenticationService;

	@PostMapping("/login")
	public ResponseEntity<ResponseLoginDTO> login(
			@Valid @RequestBody RequestLoginDTO requestLoginDTO) {
		LOG.info("POST /auth/login");

		return ResponseEntity.ok(this.authenticationService.login(requestLoginDTO));
	}

	@PostMapping("/register")
	public ResponseEntity<ResponseRegisterDTO> register(
			@Valid @RequestBody RequestRegisterDTO requestRegisterDTO) {
		LOG.info("POST /auth/register");

		return ResponseEntity.ok(this.authenticationService.register(requestRegisterDTO));
	}

	@PostMapping("/verify")
	public ResponseEntity<ResponseLoginDTO> verifyCode(
			@Valid @RequestBody VerifyMailRequest verifyMailRequest) {
		LOG.info("POST /auth/verify");

		return ResponseEntity.ok(this.authenticationService.verifyCode(verifyMailRequest));
	}

	@PostMapping("/resend-code")
	public ResponseEntity<?> resendCode(@Valid @RequestBody ResendCodeRequest resendCodeRequest) {
		LOG.info("POST /auth/resend-code");

		this.authenticationService.resendCode(resendCodeRequest);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(
			@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
		LOG.info("POST /auth/forgot-password");

		this.authenticationService.forgotPassword(forgotPasswordRequest);
		return ResponseEntity.ok().build();
	}

	/** Checks a reset code without consuming it: 200 when valid, 400 INVALID_OR_EXPIRED_CODE otherwise. */
	@PostMapping("/verify-reset-code")
	public ResponseEntity<?> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
		LOG.info("POST /auth/verify-reset-code");

		this.authenticationService.verifyResetCode(request);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/reset-password")
	public ResponseEntity<?> resetPassword(
			@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
		LOG.info("POST /auth/reset-password");

		this.authenticationService.resetPassword(resetPasswordRequest);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/refresh")
	public ResponseEntity<ResponseLoginDTO> refresh(
			@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
		LOG.info("POST /auth/refresh");

		return ResponseEntity.ok(this.authenticationService.refreshToken(refreshTokenRequest));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		LOG.info("POST /auth/logout");
		this.authenticationService.logout(request.refreshToken());

		return ResponseEntity.noContent().build();
	}

	/** Signs every other device out; this device gets a fresh pair of tokens. */
	@PostMapping("/change-password")
	public ResponseEntity<ResponseLoginDTO> changePassword(
			@Valid @RequestBody ChangePasswordRequest request) {
		LOG.info("POST /auth/change-password");
		String email = SecurityUtils.getCurrentUserLogin()
				.orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));

		return ResponseEntity.ok(this.authenticationService.changePassword(email, request));
	}
}

