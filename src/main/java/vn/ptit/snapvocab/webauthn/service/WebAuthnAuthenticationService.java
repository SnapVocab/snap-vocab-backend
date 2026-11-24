package vn.ptit.snapvocab.webauthn.service;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.UserNotActivatedException;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.service.AuthenticationService;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;
import vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsRequest;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsResponse.PublicKeyRequestOptions;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginVerifyRequest;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.PublicKeyCredentialDescriptorDTO;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;
import vn.ptit.snapvocab.webauthn.service.WebAuthnChallengeService.LoginChallenge;

@Service
@RequiredArgsConstructor
public class WebAuthnAuthenticationService {

	private static final Logger LOG = LoggerFactory.getLogger(WebAuthnAuthenticationService.class);

	private final UserRepository userRepository;
	private final WebAuthnCredentialRepository credentialRepository;
	private final WebAuthnChallengeService challengeService;
	private final AuthenticationService authenticationService;
	private final WebAuthnProperties properties;
	private final WebAuthnManager webAuthnManager;
	private final ObjectConverter objectConverter;
	private final SecurityAuditLogger securityAuditLogger;

	/**
	 * Step 1 of WebAuthn login: generates a single-use challenge in Redis and returns
	 * PublicKeyCredentialRequestOptions. When an optional email is supplied (e.g. last-used email),
	 * allowCredentials is populated so the OS prompts directly for that account's fingerprint.
	 */
	@Transactional(readOnly = true)
	public WebAuthnLoginOptionsResponse createLoginOptions(WebAuthnLoginOptionsRequest request) {
		LoginChallenge loginChallenge = challengeService.createLoginChallenge();
		List<PublicKeyCredentialDescriptorDTO> allowCredentials = resolveAllowedCredentials(request);

		return new WebAuthnLoginOptionsResponse(
				loginChallenge.challengeId(),
				new PublicKeyRequestOptions(
						loginChallenge.challenge(),
						properties.getTimeoutMs(),
						properties.getRpId(),
						allowCredentials,
						WebAuthnSupport.userVerificationRequirement(properties)
				)
		);
	}

	/**
	 * Step 2 of WebAuthn login: consumes the single-use challenge, verifies the cryptographic
	 * assertion against the stored COSE public key, updates signCount + lastUsedAt, and issues a
	 * fresh JWT + refresh token pair via AuthenticationService.
	 */
	@Transactional
	public ResponseLoginDTO verifyLogin(WebAuthnLoginVerifyRequest request) {
		String expectedChallenge = challengeService.consumeLoginChallenge(request.challengeId());
		WebAuthnCredential credential = loadCredential(request.id());
		User user = credential.getUser();

		validateAccountAndHandle(user, credential, request.response().userHandle());
		AuthenticationData authData = verifyAssertion(user, credential, request, expectedChallenge);
		recordSuccessfulUse(credential, authData);

		ResponseLoginDTO response = authenticationService.issueTokensForAuthenticatedUser(user);
		securityAuditLogger.success("WEBAUTHN_LOGIN", user.getId());
		return response;
	}

	private List<PublicKeyCredentialDescriptorDTO> resolveAllowedCredentials(WebAuthnLoginOptionsRequest request) {
		if (request == null || request.email() == null || request.email().isBlank()) {
			return List.of();
		}
		return userRepository.findOneByEmailIgnoreCase(request.email().trim())
				.filter(user -> user.isActivated() && user.getBannedAt() == null)
				.map(user -> credentialRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
						.stream()
						.map(cred -> new PublicKeyCredentialDescriptorDTO(
								"public-key",
								cred.getCredentialId(),
								WebAuthnSupport.parseTransports(cred.getTransports())
						))
						.toList())
				.orElse(List.of());
	}

	private WebAuthnCredential loadCredential(String credentialId) {
		return credentialRepository.findWithUserAndAuthoritiesByCredentialId(credentialId)
				.orElseThrow(() -> {
					securityAuditLogger.failure("WEBAUTHN_LOGIN", null, "UNKNOWN_CREDENTIAL");
					return new BadRequestAlertException("Unknown passkey credential");
				});
	}

	private void validateAccountAndHandle(User user, WebAuthnCredential credential, String submittedUserHandle) {
		if (user.getBannedAt() != null) {
			securityAuditLogger.failure("WEBAUTHN_LOGIN", user.getEmail(), "USER_BANNED");
			throw new UserNotActivatedException("USER_BANNED");
		}
		if (!user.isActivated()) {
			securityAuditLogger.failure("WEBAUTHN_LOGIN", user.getEmail(), "ACCOUNT_NOT_ACTIVATED");
			throw new UserNotActivatedException("User is not activated");
		}
		if (submittedUserHandle != null && !submittedUserHandle.isBlank()
				&& !submittedUserHandle.equals(credential.getUserHandle())) {
			securityAuditLogger.failure("WEBAUTHN_LOGIN", user.getEmail(), "USER_HANDLE_MISMATCH");
			throw new BadRequestAlertException("User handle does not match credential owner");
		}
	}

	private AuthenticationData verifyAssertion(
			User user,
			WebAuthnCredential credential,
			WebAuthnLoginVerifyRequest request,
			String expectedChallenge
	) {
		byte[] credentialIdBytes = WebAuthnSupport.decodeBase64Url(request.id(), "id");
		byte[] clientDataJSON = WebAuthnSupport.decodeBase64Url(request.response().clientDataJSON(), "clientDataJSON");
		byte[] authenticatorData = WebAuthnSupport.decodeBase64Url(
				request.response().authenticatorData(), "authenticatorData");
		byte[] signature = WebAuthnSupport.decodeBase64Url(request.response().signature(), "signature");
		String submittedUserHandle = request.response().userHandle();
		byte[] userHandleBytes = (submittedUserHandle == null || submittedUserHandle.isBlank())
				? null
				: WebAuthnSupport.decodeBase64Url(submittedUserHandle, "userHandle");

		CredentialRecord credentialRecord = toCredentialRecord(credential, credentialIdBytes);
		ServerProperty serverProperty = WebAuthnSupport.buildServerProperty(properties, expectedChallenge);
		AuthenticationRequest authRequest = new AuthenticationRequest(
				credentialIdBytes, userHandleBytes, authenticatorData, clientDataJSON, null, signature);
		AuthenticationParameters authParams = new AuthenticationParameters(
				serverProperty, credentialRecord, List.of(credentialIdBytes),
				properties.isRequireUserVerification(), true);

		try {
			return webAuthnManager.verify(authRequest, authParams);
		} catch (DataConversionException | VerificationException e) {
			LOG.warn("WebAuthn login verification failed for userId={}: {}", user.getId(), e.getMessage());
			securityAuditLogger.failure("WEBAUTHN_LOGIN", user.getEmail(), "INVALID_ASSERTION");
			throw new BadRequestAlertException("Invalid WebAuthn login assertion: " + e.getMessage());
		}
	}

	private CredentialRecord toCredentialRecord(WebAuthnCredential credential, byte[] credentialIdBytes) {
		AttestedCredentialData attestedData = new AttestedCredentialData(
				parseAaguid(credential.getAaguid()),
				credentialIdBytes,
				deserializeCoseKey(credential.getPublicKeyCose())
		);
		return new CredentialRecordImpl(
				null,
				null,
				credential.isBackupEligible(),
				credential.isBackupState(),
				credential.getSignCount(),
				attestedData,
				null,
				null,
				null,
				null
		);
	}

	private void recordSuccessfulUse(WebAuthnCredential credential, AuthenticationData authData) {
		credential.setSignCount(authData.getAuthenticatorData().getSignCount());
		credential.setBackupEligible(authData.getAuthenticatorData().isFlagBE());
		credential.setBackupState(authData.getAuthenticatorData().isFlagBS());
		credential.setLastUsedAt(Instant.now());
		credentialRepository.save(credential);
	}

	private COSEKey deserializeCoseKey(byte[] publicKeyCose) {
		try {
			COSEKey key = objectConverter.getCborConverter().readValue(publicKeyCose, COSEKey.class);
			if (key == null) {
				throw new BadRequestAlertException("Stored credential public key is invalid");
			}
			return key;
		} catch (RuntimeException e) {
			if (e instanceof BadRequestAlertException) {
				throw e;
			}
			throw new BadRequestAlertException("Failed to deserialize stored credential public key");
		}
	}

	private AAGUID parseAaguid(String aaguid) {
		if (aaguid == null || aaguid.isBlank()) {
			return AAGUID.ZERO;
		}
		try {
			return new AAGUID(aaguid);
		} catch (IllegalArgumentException e) {
			return AAGUID.ZERO;
		}
	}
}
