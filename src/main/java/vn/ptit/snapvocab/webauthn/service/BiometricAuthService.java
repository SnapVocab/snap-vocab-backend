package vn.ptit.snapvocab.webauthn.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.common.exception.UserNotActivatedException;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.service.AuthenticationService;
import vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;

/**
 * Hardware-backed native BiometricPrompt authentication (Android Keystore / iOS Keychain).
 * Stores only the SHA-256 hash of the high-entropy device secret in {@code user_webauthn_credentials}.
 */
@Service
@RequiredArgsConstructor
public class BiometricAuthService {

	public static final String BIOMETRIC_TRANSPORT = "biometric_device";
	private static final int SECRET_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final UserRepository userRepository;
	private final WebAuthnCredentialRepository credentialRepository;
	private final AuthenticationService authenticationService;
	private final SecurityAuditLogger securityAuditLogger;

	public record BiometricRegisterResponse(
			Long id,
			String credentialId,
			String biometricToken,
			String deviceName,
			Instant createdAt
	) {}

	@Transactional
	public BiometricRegisterResponse registerDevice(String email, String deviceName) {
		User user = userRepository.findOneByEmailIgnoreCase(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
		if (user.getBannedAt() != null) {
			throw new UserNotActivatedException("USER_BANNED");
		}
		if (!user.isActivated()) {
			throw new UserNotActivatedException("User is not activated");
		}

		byte[] rawSecret = new byte[SECRET_BYTES];
		secureRandom.nextBytes(rawSecret);
		String biometricToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSecret);
		byte[] tokenHash = sha256(biometricToken);
		String credentialId = "bio:" + UUID.randomUUID();
		String normalizedName = (deviceName == null || deviceName.isBlank())
				? "Biometric Device"
				: deviceName.trim().substring(0, Math.min(deviceName.trim().length(), 120));

		WebAuthnCredential entity = WebAuthnCredential.builder()
				.user(user)
				.credentialId(credentialId)
				.userHandle(WebAuthnSupport.encodeUserHandle(user.getId()))
				.publicKeyCose(tokenHash)
				.signCount(0L)
				.transports(BIOMETRIC_TRANSPORT)
				.deviceName(normalizedName)
				.build();

		WebAuthnCredential saved = credentialRepository.save(entity);
		securityAuditLogger.success("BIOMETRIC_REGISTER", user.getId());
		return new BiometricRegisterResponse(
				saved.getId(),
				saved.getCredentialId(),
				biometricToken,
				saved.getDeviceName(),
				saved.getCreatedAt()
		);
	}

	@Transactional
	public ResponseLoginDTO loginWithBiometric(String credentialId, String biometricToken) {
		WebAuthnCredential credential = credentialRepository
				.findWithUserAndAuthoritiesByCredentialId(credentialId)
				.orElseThrow(() -> {
					securityAuditLogger.failure("BIOMETRIC_LOGIN", null, "UNKNOWN_CREDENTIAL");
					return new BadRequestAlertException("Biometric credential not found");
				});

		User user = credential.getUser();
		if (!BIOMETRIC_TRANSPORT.equals(credential.getTransports())) {
			securityAuditLogger.failure("BIOMETRIC_LOGIN", user.getEmail(), "INVALID_CREDENTIAL_TYPE");
			throw new BadRequestAlertException("Invalid biometric credential");
		}
		if (user.getBannedAt() != null) {
			securityAuditLogger.failure("BIOMETRIC_LOGIN", user.getEmail(), "USER_BANNED");
			throw new UserNotActivatedException("USER_BANNED");
		}
		if (!user.isActivated()) {
			securityAuditLogger.failure("BIOMETRIC_LOGIN", user.getEmail(), "ACCOUNT_NOT_ACTIVATED");
			throw new UserNotActivatedException("User is not activated");
		}

		byte[] submittedHash = sha256(biometricToken);
		if (!MessageDigest.isEqual(credential.getPublicKeyCose(), submittedHash)) {
			securityAuditLogger.failure("BIOMETRIC_LOGIN", user.getEmail(), "INVALID_TOKEN");
			throw new BadRequestAlertException("Invalid biometric credential");
		}

		credential.setSignCount(credential.getSignCount() + 1);
		credential.setLastUsedAt(Instant.now());
		credentialRepository.save(credential);

		ResponseLoginDTO response = authenticationService.issueTokensForAuthenticatedUser(user);
		securityAuditLogger.success("BIOMETRIC_LOGIN", user.getId());
		return response;
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}

