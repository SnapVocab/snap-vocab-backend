package vn.ptit.snapvocab.webauthn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;
import vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnCredentialResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterVerifyRequest;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterVerifyRequest.AttestationResponseDTO;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;

@ExtendWith(MockitoExtension.class)
class WebAuthnRegistrationServiceTest {

	private static final String EMAIL = "learner@example.com";
	private static final String RP_ID = "localhost";
	private static final String ORIGIN = "http://localhost:5173";

	@Mock
	private UserRepository userRepository;
	@Mock
	private WebAuthnCredentialRepository credentialRepository;
	@Mock
	private WebAuthnChallengeService challengeService;
	@Mock
	private SecurityAuditLogger securityAuditLogger;

	private ObjectConverter objectConverter;
	private WebAuthnProperties properties;
	private WebAuthnRegistrationService service;
	private User user;

	@BeforeEach
	void setUp() {
		objectConverter = new ObjectConverter();
		WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
		properties = new WebAuthnProperties();
		properties.setRpId(RP_ID);
		properties.setRpName("SnapVocab");
		properties.setAllowedOrigins(List.of(ORIGIN));
		properties.setTimeoutMs(60000L);
		properties.setRequireUserVerification(true);

		service = new WebAuthnRegistrationService(
				userRepository,
				credentialRepository,
				challengeService,
				properties,
				webAuthnManager,
				objectConverter,
				securityAuditLogger
		);

		user = User.builder()
				.id(7L)
				.email(EMAIL)
				.firstName("Thanh")
				.lastName("Nguyen")
				.password("x".repeat(60))
				.activated(true)
				.build();
	}

	@Test
	void createRegistrationOptionsBuildsW3cOptionsAndExcludesExistingCredentials() {
		WebAuthnCredential existing = WebAuthnCredential.builder()
				.id(1L)
				.user(user)
				.credentialId("existing-cred-id")
				.transports("internal,hybrid")
				.build();
		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(challengeService.createRegistrationChallenge(7L)).thenReturn("random-challenge-b64url");
		when(credentialRepository.findAllByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(existing));

		WebAuthnRegisterOptionsResponse options = service.createRegistrationOptions(EMAIL);

		assertThat(options.challenge()).isEqualTo("random-challenge-b64url");
		assertThat(options.rp().id()).isEqualTo(RP_ID);
		assertThat(options.rp().name()).isEqualTo("SnapVocab");
		assertThat(options.user().id()).isEqualTo(WebAuthnRegistrationService.encodeUserHandle(7L));
		assertThat(options.user().name()).isEqualTo(EMAIL);
		assertThat(options.user().displayName()).isEqualTo("Thanh Nguyen");
		assertThat(options.authenticatorSelection().authenticatorAttachment()).isEqualTo("platform");
		assertThat(options.authenticatorSelection().userVerification()).isEqualTo("required");
		assertThat(options.excludeCredentials()).hasSize(1);
		assertThat(options.excludeCredentials().get(0).id()).isEqualTo("existing-cred-id");
		assertThat(options.excludeCredentials().get(0).transports()).containsExactly("internal", "hybrid");
	}

	@Test
	void verifyRegistrationValidatesRealAttestationAndSavesCosePublicKey() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("test-challenge-32-bytes-12345678".getBytes(StandardCharsets.UTF_8));
		byte[] credentialIdBytes = "device-credential-id-001".getBytes(StandardCharsets.UTF_8);
		String credentialId = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialIdBytes);

		WebAuthnRegisterVerifyRequest request = buildSignedRegistrationRequest(
				credentialIdBytes, challenge, ORIGIN, RP_ID, true, "Windows Hello"
		);

		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(challengeService.consumeRegistrationChallenge(7L)).thenReturn(challenge);
		when(credentialRepository.existsByCredentialId(credentialId)).thenReturn(false);
		when(credentialRepository.save(any(WebAuthnCredential.class))).thenAnswer(inv -> {
			WebAuthnCredential saved = inv.getArgument(0);
			saved.setId(99L);
			return saved;
		});

		WebAuthnCredentialResponse response = service.verifyRegistration(EMAIL, request);

		assertThat(response.id()).isEqualTo(99L);
		assertThat(response.credentialId()).isEqualTo(credentialId);
		assertThat(response.deviceName()).isEqualTo("Windows Hello");
		assertThat(response.transports()).containsExactly("internal");

		ArgumentCaptor<WebAuthnCredential> captor = ArgumentCaptor.forClass(WebAuthnCredential.class);
		verify(credentialRepository).save(captor.capture());
		WebAuthnCredential stored = captor.getValue();
		assertThat(stored.getPublicKeyCose()).isNotEmpty();
		assertThat(stored.getUserHandle()).isEqualTo(WebAuthnRegistrationService.encodeUserHandle(7L));
		assertThat(stored.getSignCount()).isEqualTo(1L);
		verify(securityAuditLogger).success("WEBAUTHN_REGISTER", 7L);
	}

	@Test
	void verifyRegistrationRejectsMismatchedOrigin() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("test-challenge-32-bytes-12345678".getBytes(StandardCharsets.UTF_8));
		byte[] credentialIdBytes = "device-credential-id-002".getBytes(StandardCharsets.UTF_8);

		WebAuthnRegisterVerifyRequest request = buildSignedRegistrationRequest(
				credentialIdBytes, challenge, "https://evil-phishing.example", RP_ID, true, "Evil"
		);

		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(challengeService.consumeRegistrationChallenge(7L)).thenReturn(challenge);

		assertThatThrownBy(() -> service.verifyRegistration(EMAIL, request))
				.isInstanceOf(BadRequestAlertException.class)
				.hasMessageContaining("origin");
		verify(credentialRepository, never()).save(any());
		verify(securityAuditLogger).failure("WEBAUTHN_REGISTER", EMAIL, "INVALID_ATTESTATION");
	}

	@Test
	void verifyRegistrationRejectsWhenUserVerificationFlagIsMissing() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("test-challenge-32-bytes-12345678".getBytes(StandardCharsets.UTF_8));
		byte[] credentialIdBytes = "device-credential-id-003".getBytes(StandardCharsets.UTF_8);

		WebAuthnRegisterVerifyRequest request = buildSignedRegistrationRequest(
				credentialIdBytes, challenge, ORIGIN, RP_ID, false, "No UV Device"
		);

		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(challengeService.consumeRegistrationChallenge(7L)).thenReturn(challenge);

		assertThatThrownBy(() -> service.verifyRegistration(EMAIL, request))
				.isInstanceOf(BadRequestAlertException.class)
				.hasMessageContaining("UV");
		verify(credentialRepository, never()).save(any());
	}

	@Test
	void verifyRegistrationRejectsDuplicateCredentialId() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("test-challenge-32-bytes-12345678".getBytes(StandardCharsets.UTF_8));
		byte[] credentialIdBytes = "device-credential-id-004".getBytes(StandardCharsets.UTF_8);
		String credentialId = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialIdBytes);

		WebAuthnRegisterVerifyRequest request = buildSignedRegistrationRequest(
				credentialIdBytes, challenge, ORIGIN, RP_ID, true, "Duplicate"
		);

		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(challengeService.consumeRegistrationChallenge(7L)).thenReturn(challenge);
		when(credentialRepository.existsByCredentialId(credentialId)).thenReturn(true);

		assertThatThrownBy(() -> service.verifyRegistration(EMAIL, request))
				.isInstanceOf(ResourceAlreadyExistException.class);
		verify(credentialRepository, never()).save(any());
	}

	@Test
	void deleteCredentialThrowsNotFoundWhenNotOwnedByUser() {
		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(credentialRepository.findByIdAndUserId(55L, 7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteCredential(EMAIL, 55L))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private WebAuthnRegisterVerifyRequest buildSignedRegistrationRequest(
			byte[] credentialIdBytes,
			String challengeBase64Url,
			String origin,
			String rpId,
			boolean userVerified,
			String deviceName
	) throws Exception {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
		keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		EC2COSEKey coseKey = EC2COSEKey.create((ECPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.ES256);

		byte[] rpIdHash = MessageDigest.getInstance("SHA-256")
				.digest(rpId.getBytes(StandardCharsets.UTF_8));

		byte flags = (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_AT
				| (userVerified ? AuthenticatorData.BIT_UV : 0));

		AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
				AAGUID.ZERO,
				credentialIdBytes,
				coseKey
		);
		AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authenticatorData = new AuthenticatorData<>(
				rpIdHash,
				flags,
				1L,
				attestedCredentialData
		);
		AttestationObject attestationObject = new AttestationObject(
				authenticatorData,
				new NoneAttestationStatement()
		);

		byte[] attestationBytes = new AttestationObjectConverter(objectConverter)
				.convertToBytes(attestationObject);

		String clientDataJson = String.format(
				"{\"type\":\"webauthn.create\",\"challenge\":\"%s\",\"origin\":\"%s\",\"crossOrigin\":false}",
				challengeBase64Url,
				origin
		);

		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		return new WebAuthnRegisterVerifyRequest(
				encoder.encodeToString(credentialIdBytes),
				new AttestationResponseDTO(
						encoder.encodeToString(clientDataJson.getBytes(StandardCharsets.UTF_8)),
						encoder.encodeToString(attestationBytes),
						List.of("internal")
				),
				deviceName
		);
	}
}

