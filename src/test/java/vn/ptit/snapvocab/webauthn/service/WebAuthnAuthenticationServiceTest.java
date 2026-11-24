package vn.ptit.snapvocab.webauthn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AuthenticatorDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginVerifyRequest;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginVerifyRequest.AssertionResponseDTO;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;
import vn.ptit.snapvocab.webauthn.service.WebAuthnChallengeService.LoginChallenge;

@ExtendWith(MockitoExtension.class)
class WebAuthnAuthenticationServiceTest {

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
	private AuthenticationService authenticationService;
	@Mock
	private SecurityAuditLogger securityAuditLogger;

	private ObjectConverter objectConverter;
	private WebAuthnAuthenticationService service;
	private User user;
	private KeyPair registeredKeyPair;
	private WebAuthnCredential storedCredential;
	private String credentialId;

	@BeforeEach
	void setUp() throws Exception {
		objectConverter = new ObjectConverter();
		WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
		WebAuthnProperties properties = new WebAuthnProperties();
		properties.setRpId(RP_ID);
		properties.setRpName("SnapVocab");
		properties.setAllowedOrigins(List.of(ORIGIN));
		properties.setTimeoutMs(60000L);
		properties.setRequireUserVerification(true);

		service = new WebAuthnAuthenticationService(
				userRepository,
				credentialRepository,
				challengeService,
				authenticationService,
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

		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp256r1"));
		registeredKeyPair = kpg.generateKeyPair();
		EC2COSEKey coseKey = EC2COSEKey.create((ECPublicKey) registeredKeyPair.getPublic(), COSEAlgorithmIdentifier.ES256);
		byte[] publicKeyCose = objectConverter.getCborConverter().writeValueAsBytes(coseKey);

		credentialId = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("cred-login-001".getBytes(StandardCharsets.UTF_8));

		storedCredential = WebAuthnCredential.builder()
				.id(10L)
				.user(user)
				.credentialId(credentialId)
				.userHandle(WebAuthnRegistrationService.encodeUserHandle(7L))
				.publicKeyCose(publicKeyCose)
				.signCount(5L)
				.transports("internal")
				.deviceName("Windows Hello")
				.build();
	}

	@Test
	void createLoginOptionsWithSavedEmailPopulatesAllowCredentials() {
		when(challengeService.createLoginChallenge())
				.thenReturn(new LoginChallenge("cid-1", "login-challenge-b64url"));
		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(credentialRepository.findAllByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(storedCredential));

		WebAuthnLoginOptionsResponse response = service.createLoginOptions(new WebAuthnLoginOptionsRequest(EMAIL));

		assertThat(response.challengeId()).isEqualTo("cid-1");
		assertThat(response.publicKey().challenge()).isEqualTo("login-challenge-b64url");
		assertThat(response.publicKey().rpId()).isEqualTo(RP_ID);
		assertThat(response.publicKey().allowCredentials()).hasSize(1);
		assertThat(response.publicKey().allowCredentials().get(0).id()).isEqualTo(credentialId);
	}

	@Test
	void createLoginOptionsWithoutEmailReturnsEmptyAllowCredentialsForPasskeyFlow() {
		when(challengeService.createLoginChallenge())
				.thenReturn(new LoginChallenge("cid-2", "login-challenge-b64url"));

		WebAuthnLoginOptionsResponse response = service.createLoginOptions(null);

		assertThat(response.challengeId()).isEqualTo("cid-2");
		assertThat(response.publicKey().allowCredentials()).isEmpty();
		verify(userRepository, never()).findOneByEmailIgnoreCase(any());
	}

	@Test
	void verifyLoginValidatesRealEcdsaSignatureUpdatesSignCountAndIssuesTokens() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("login-challenge-32-bytes-1234567".getBytes(StandardCharsets.UTF_8));
		WebAuthnLoginVerifyRequest request = buildSignedAssertionRequest(
				"cid-1", credentialId, storedCredential.getUserHandle(), challenge, 6L, registeredKeyPair
		);

		ResponseLoginDTO expectedTokens = ResponseLoginDTO.builder()
				.accessToken("jwt-access")
				.refreshToken("refresh-123")
				.build();

		when(challengeService.consumeLoginChallenge("cid-1")).thenReturn(challenge);
		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(credentialId))
				.thenReturn(Optional.of(storedCredential));
		when(credentialRepository.save(any(WebAuthnCredential.class))).thenAnswer(inv -> inv.getArgument(0));
		when(authenticationService.issueTokensForAuthenticatedUser(user)).thenReturn(expectedTokens);

		ResponseLoginDTO result = service.verifyLogin(request);

		assertThat(result.getAccessToken()).isEqualTo("jwt-access");
		assertThat(result.getRefreshToken()).isEqualTo("refresh-123");
		assertThat(storedCredential.getSignCount()).isEqualTo(6L);
		assertThat(storedCredential.getLastUsedAt()).isNotNull();
		verify(credentialRepository).save(storedCredential);
		verify(securityAuditLogger).success("WEBAUTHN_LOGIN", 7L);
	}

	@Test
	void verifyLoginRejectsSignatureFromWrongPrivateKey() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair attackerKeyPair = kpg.generateKeyPair();

		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("login-challenge-32-bytes-1234567".getBytes(StandardCharsets.UTF_8));
		WebAuthnLoginVerifyRequest request = buildSignedAssertionRequest(
				"cid-1", credentialId, storedCredential.getUserHandle(), challenge, 6L, attackerKeyPair
		);

		when(challengeService.consumeLoginChallenge("cid-1")).thenReturn(challenge);
		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(credentialId))
				.thenReturn(Optional.of(storedCredential));

		assertThatThrownBy(() -> service.verifyLogin(request))
				.isInstanceOf(BadRequestAlertException.class)
				.hasMessageContaining("Invalid WebAuthn login assertion");
		verify(authenticationService, never()).issueTokensForAuthenticatedUser(any());
		verify(securityAuditLogger).failure("WEBAUTHN_LOGIN", EMAIL, "INVALID_ASSERTION");
	}

	@Test
	void verifyLoginRejectsClonedSignCount() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("login-challenge-32-bytes-1234567".getBytes(StandardCharsets.UTF_8));
		// Stored signCount is 5; submitting 5 simulates a cloned authenticator.
		WebAuthnLoginVerifyRequest request = buildSignedAssertionRequest(
				"cid-1", credentialId, storedCredential.getUserHandle(), challenge, 5L, registeredKeyPair
		);

		when(challengeService.consumeLoginChallenge("cid-1")).thenReturn(challenge);
		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(credentialId))
				.thenReturn(Optional.of(storedCredential));

		assertThatThrownBy(() -> service.verifyLogin(request))
				.isInstanceOf(BadRequestAlertException.class)
				.hasMessageContaining("Invalid WebAuthn login assertion");
		verify(authenticationService, never()).issueTokensForAuthenticatedUser(any());
	}

	@Test
	void verifyLoginRejectsBannedUserBeforeIssuingTokens() throws Exception {
		user.setBannedAt(Instant.now());
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("login-challenge-32-bytes-1234567".getBytes(StandardCharsets.UTF_8));
		WebAuthnLoginVerifyRequest request = buildSignedAssertionRequest(
				"cid-1", credentialId, storedCredential.getUserHandle(), challenge, 6L, registeredKeyPair
		);

		when(challengeService.consumeLoginChallenge("cid-1")).thenReturn(challenge);
		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(credentialId))
				.thenReturn(Optional.of(storedCredential));

		assertThatThrownBy(() -> service.verifyLogin(request))
				.isInstanceOf(UserNotActivatedException.class)
				.hasMessage("USER_BANNED");
		verify(authenticationService, never()).issueTokensForAuthenticatedUser(any());
	}

	@Test
	void verifyLoginRejectsMismatchedUserHandle() throws Exception {
		String challenge = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("login-challenge-32-bytes-1234567".getBytes(StandardCharsets.UTF_8));
		String otherUserHandle = WebAuthnRegistrationService.encodeUserHandle(999L);
		WebAuthnLoginVerifyRequest request = buildSignedAssertionRequest(
				"cid-1", credentialId, otherUserHandle, challenge, 6L, registeredKeyPair
		);

		when(challengeService.consumeLoginChallenge("cid-1")).thenReturn(challenge);
		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(credentialId))
				.thenReturn(Optional.of(storedCredential));

		assertThatThrownBy(() -> service.verifyLogin(request))
				.isInstanceOf(BadRequestAlertException.class)
				.hasMessageContaining("User handle");
		verify(authenticationService, never()).issueTokensForAuthenticatedUser(any());
	}

	private WebAuthnLoginVerifyRequest buildSignedAssertionRequest(
			String challengeId,
			String credId,
			String userHandle,
			String challengeBase64Url,
			long signCount,
			KeyPair signingKeyPair
	) throws Exception {
		byte[] rpIdHash = MessageDigest.getInstance("SHA-256")
				.digest(RP_ID.getBytes(StandardCharsets.UTF_8));
		byte flags = (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV);

		AuthenticatorData<AuthenticationExtensionAuthenticatorOutput> authData =
				new AuthenticatorData<>(rpIdHash, flags, signCount);
		byte[] authDataBytes = new AuthenticatorDataConverter(objectConverter).convert(authData);

		String clientDataJson = String.format(
				"{\"type\":\"webauthn.get\",\"challenge\":\"%s\",\"origin\":\"%s\",\"crossOrigin\":false}",
				challengeBase64Url,
				ORIGIN
		);
		byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
		byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes);

		ByteArrayOutputStream signedPayload = new ByteArrayOutputStream();
		signedPayload.write(authDataBytes);
		signedPayload.write(clientDataHash);

		Signature ecdsa = Signature.getInstance("SHA256withECDSA");
		ecdsa.initSign(signingKeyPair.getPrivate());
		ecdsa.update(signedPayload.toByteArray());
		byte[] signatureBytes = ecdsa.sign();

		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		return new WebAuthnLoginVerifyRequest(
				challengeId,
				credId,
				new AssertionResponseDTO(
						encoder.encodeToString(clientDataBytes),
						encoder.encodeToString(authDataBytes),
						encoder.encodeToString(signatureBytes),
						userHandle
				)
		);
	}
}

