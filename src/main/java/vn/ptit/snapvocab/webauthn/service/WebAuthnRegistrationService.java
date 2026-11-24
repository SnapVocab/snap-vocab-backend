package vn.ptit.snapvocab.webauthn.service;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.common.exception.UserNotActivatedException;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;
import vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnCredentialResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.AuthenticatorSelection;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.PubKeyCredParam;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.PublicKeyCredentialDescriptorDTO;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.RelyingParty;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.UserEntity;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterVerifyRequest;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;

@Service
@RequiredArgsConstructor
public class WebAuthnRegistrationService {

	private static final Logger LOG = LoggerFactory.getLogger(WebAuthnRegistrationService.class);

	private static final List<PublicKeyCredentialParameters> SUPPORTED_ALGORITHMS = List.of(
			new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
			new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256)
	);

	private static final List<PubKeyCredParam> PUB_KEY_CRED_PARAMS = List.of(
			new PubKeyCredParam("public-key", COSEAlgorithmIdentifier.ES256.getValue()),
			new PubKeyCredParam("public-key", COSEAlgorithmIdentifier.RS256.getValue())
	);

	private final UserRepository userRepository;
	private final WebAuthnCredentialRepository credentialRepository;
	private final WebAuthnChallengeService challengeService;
	private final WebAuthnProperties properties;
	private final WebAuthnManager webAuthnManager;
	private final ObjectConverter objectConverter;
	private final SecurityAuditLogger securityAuditLogger;

	/**
	 * Step 1 of WebAuthn registration: generates PublicKeyCredentialCreationOptions and stores a
	 * single-use challenge in Redis for the authenticated user.
	 */
	@Transactional(readOnly = true)
	public WebAuthnRegisterOptionsResponse createRegistrationOptions(String email) {
		User user = loadActiveUser(email);
		String challenge = challengeService.createRegistrationChallenge(user.getId());

		List<PublicKeyCredentialDescriptorDTO> excludeCredentials = credentialRepository
				.findAllByUserIdOrderByCreatedAtDesc(user.getId())
				.stream()
				.map(cred -> new PublicKeyCredentialDescriptorDTO(
						"public-key",
						cred.getCredentialId(),
						WebAuthnSupport.parseTransports(cred.getTransports())
				))
				.toList();

		return new WebAuthnRegisterOptionsResponse(
				challenge,
				new RelyingParty(properties.getRpId(), properties.getRpName()),
				new UserEntity(WebAuthnSupport.encodeUserHandle(user.getId()), user.getEmail(), buildDisplayName(user)),
				PUB_KEY_CRED_PARAMS,
				properties.getTimeoutMs(),
				"none",
				new AuthenticatorSelection(
						"platform",
						"preferred",
						false,
						WebAuthnSupport.userVerificationRequirement(properties)
				),
				excludeCredentials
		);
	}

	/**
	 * Step 2 of WebAuthn registration: consumes the Redis challenge, cryptographically verifies
	 * clientDataJSON + attestationObject, and persists the device's COSE public key.
	 */
	@Transactional
	public WebAuthnCredentialResponse verifyRegistration(String email, WebAuthnRegisterVerifyRequest request) {
		User user = loadActiveUser(email);
		String expectedChallenge = challengeService.consumeRegistrationChallenge(user.getId());
		Set<String> transports = WebAuthnSupport.normalizeTransports(request.response().transports());

		RegistrationData registrationData = verifyAttestation(user, request, expectedChallenge, transports);
		AuthenticatorData<?> authenticatorData = registrationData.getAttestationObject().getAuthenticatorData();
		AttestedCredentialData attestedData = extractAndValidateCredentialData(user, authenticatorData, request.id());

		WebAuthnCredential entity = buildCredentialEntity(
				user, authenticatorData, attestedData, request.id(), transports, request.deviceName());
		WebAuthnCredential saved = credentialRepository.save(entity);

		securityAuditLogger.success("WEBAUTHN_REGISTER", user.getId());
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<WebAuthnCredentialResponse> listCredentials(String email) {
		User user = loadActiveUser(email);
		return credentialRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void deleteCredential(String email, Long credentialDbId) {
		User user = loadActiveUser(email);
		WebAuthnCredential credential = credentialRepository.findByIdAndUserId(credentialDbId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Passkey not found: " + credentialDbId));
		credentialRepository.delete(credential);
		securityAuditLogger.success("WEBAUTHN_REVOKE", user.getId());
	}

	static String encodeUserHandle(Long userId) {
		return WebAuthnSupport.encodeUserHandle(userId);
	}

	private RegistrationData verifyAttestation(
			User user,
			WebAuthnRegisterVerifyRequest request,
			String expectedChallenge,
			Set<String> transports
	) {
		byte[] clientDataJSON = WebAuthnSupport.decodeBase64Url(
				request.response().clientDataJSON(), "clientDataJSON");
		byte[] attestationObject = WebAuthnSupport.decodeBase64Url(
				request.response().attestationObject(), "attestationObject");

		ServerProperty serverProperty = WebAuthnSupport.buildServerProperty(properties, expectedChallenge);
		RegistrationRequest registrationRequest = new RegistrationRequest(
				attestationObject, clientDataJSON, null, transports);
		RegistrationParameters registrationParameters = new RegistrationParameters(
				serverProperty, SUPPORTED_ALGORITHMS, properties.isRequireUserVerification(), true);

		try {
			return webAuthnManager.verify(registrationRequest, registrationParameters);
		} catch (DataConversionException | VerificationException e) {
			LOG.warn("WebAuthn registration verification failed for userId={}: {}", user.getId(), e.getMessage());
			securityAuditLogger.failure("WEBAUTHN_REGISTER", user.getEmail(), "INVALID_ATTESTATION");
			throw new BadRequestAlertException("Invalid WebAuthn registration response: " + e.getMessage());
		}
	}

	private AttestedCredentialData extractAndValidateCredentialData(
			User user,
			AuthenticatorData<?> authenticatorData,
			String requestedCredentialId
	) {
		AttestedCredentialData attestedData = authenticatorData.getAttestedCredentialData();
		if (attestedData == null) {
			securityAuditLogger.failure("WEBAUTHN_REGISTER", user.getEmail(), "MISSING_CREDENTIAL_DATA");
			throw new BadRequestAlertException("Attestation does not contain credential data");
		}

		String credentialId = WebAuthnSupport.encodeBase64Url(attestedData.getCredentialId());
		if (!credentialId.equals(requestedCredentialId)) {
			securityAuditLogger.failure("WEBAUTHN_REGISTER", user.getEmail(), "CREDENTIAL_ID_MISMATCH");
			throw new BadRequestAlertException("Credential ID in request does not match attestation data");
		}

		if (credentialRepository.existsByCredentialId(credentialId)) {
			securityAuditLogger.failure("WEBAUTHN_REGISTER", user.getEmail(), "CREDENTIAL_ALREADY_REGISTERED");
			throw new ResourceAlreadyExistException("This authenticator is already registered");
		}

		return attestedData;
	}

	private WebAuthnCredential buildCredentialEntity(
			User user,
			AuthenticatorData<?> authenticatorData,
			AttestedCredentialData attestedData,
			String credentialId,
			Set<String> transports,
			String deviceName
	) {
		byte[] publicKeyCose = objectConverter.getCborConverter().writeValueAsBytes(attestedData.getCOSEKey());
		String aaguid = attestedData.getAaguid() != null ? attestedData.getAaguid().toString() : null;

		return WebAuthnCredential.builder()
				.user(user)
				.credentialId(credentialId)
				.userHandle(WebAuthnSupport.encodeUserHandle(user.getId()))
				.publicKeyCose(publicKeyCose)
				.aaguid(aaguid)
				.signCount(authenticatorData.getSignCount())
				.transports(WebAuthnSupport.serializeTransports(transports))
				.deviceName(normalizeDeviceName(deviceName))
				.backupEligible(authenticatorData.isFlagBE())
				.backupState(authenticatorData.isFlagBS())
				.build();
	}

	private User loadActiveUser(String email) {
		User user = userRepository.findOneByEmailIgnoreCase(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
		if (user.getBannedAt() != null) {
			throw new UserNotActivatedException("USER_BANNED");
		}
		if (!user.isActivated()) {
			throw new UserNotActivatedException("User is not activated");
		}
		return user;
	}

	private String buildDisplayName(User user) {
		String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
		String last = user.getLastName() != null ? user.getLastName().trim() : "";
		String full = (first + " " + last).trim();
		return full.isEmpty() ? user.getEmail() : full;
	}

	private String normalizeDeviceName(String deviceName) {
		if (deviceName == null || deviceName.isBlank()) {
			return "Biometric Device";
		}
		return deviceName.trim();
	}

	private WebAuthnCredentialResponse toResponse(WebAuthnCredential entity) {
		return new WebAuthnCredentialResponse(
				entity.getId(),
				entity.getCredentialId(),
				entity.getDeviceName(),
				WebAuthnSupport.parseTransports(entity.getTransports()),
				entity.isBackupEligible(),
				entity.isBackupState(),
				entity.getCreatedAt(),
				entity.getLastUsedAt()
		);
	}
}
