package vn.ptit.snapvocab.webauthn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.UserNotActivatedException;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.service.AuthenticationService;
import vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;
import vn.ptit.snapvocab.webauthn.service.BiometricAuthService.BiometricRegisterResponse;

@ExtendWith(MockitoExtension.class)
class BiometricAuthServiceTest {

	private static final String EMAIL = "learner@example.com";

	@Mock
	private UserRepository userRepository;
	@Mock
	private WebAuthnCredentialRepository credentialRepository;
	@Mock
	private AuthenticationService authenticationService;
	@Mock
	private SecurityAuditLogger securityAuditLogger;

	private BiometricAuthService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new BiometricAuthService(
				userRepository,
				credentialRepository,
				authenticationService,
				securityAuditLogger
		);
		user = User.builder()
				.id(7L)
				.email(EMAIL)
				.firstName("Thanh")
				.password("x".repeat(60))
				.activated(true)
				.build();
	}

	@Test
	void registerStoresHashedSecretAndLoginVerifiesIt() {
		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(credentialRepository.save(any(WebAuthnCredential.class))).thenAnswer(inv -> {
			WebAuthnCredential c = inv.getArgument(0);
			if (c.getId() == null) {
				c.setId(55L);
			}
			return c;
		});

		BiometricRegisterResponse registered = service.registerDevice(EMAIL, "HONOR Magic");
		assertThat(registered.id()).isEqualTo(55L);
		assertThat(registered.credentialId()).startsWith("bio:");
		assertThat(registered.biometricToken()).isNotBlank();

		ArgumentCaptor<WebAuthnCredential> captor = ArgumentCaptor.forClass(WebAuthnCredential.class);
		verify(credentialRepository).save(captor.capture());
		WebAuthnCredential stored = captor.getValue();
		assertThat(stored.getPublicKeyCose()).hasSize(32); // SHA-256 hash, never plaintext

		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(registered.credentialId()))
				.thenReturn(Optional.of(stored));
		when(authenticationService.issueTokensForAuthenticatedUser(user))
				.thenReturn(ResponseLoginDTO.builder().accessToken("jwt").refreshToken("ref").build());

		ResponseLoginDTO loginRes = service.loginWithBiometric(
				registered.credentialId(), registered.biometricToken());
		assertThat(loginRes.getAccessToken()).isEqualTo("jwt");
		assertThat(stored.getSignCount()).isEqualTo(1L);
		assertThat(stored.getLastUsedAt()).isNotNull();
	}

	@Test
	void loginRejectsWrongTokenAndBannedUser() {
		when(userRepository.findOneByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		when(credentialRepository.save(any(WebAuthnCredential.class))).thenAnswer(inv -> inv.getArgument(0));

		BiometricRegisterResponse registered = service.registerDevice(EMAIL, "Phone");
		ArgumentCaptor<WebAuthnCredential> captor = ArgumentCaptor.forClass(WebAuthnCredential.class);
		verify(credentialRepository).save(captor.capture());
		WebAuthnCredential stored = captor.getValue();

		when(credentialRepository.findWithUserAndAuthoritiesByCredentialId(registered.credentialId()))
				.thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.loginWithBiometric(registered.credentialId(), "wrong-token"))
				.isInstanceOf(BadRequestAlertException.class);
		verify(authenticationService, never()).issueTokensForAuthenticatedUser(any());

		user.setBannedAt(Instant.now());
		assertThatThrownBy(() -> service.loginWithBiometric(
				registered.credentialId(), registered.biometricToken()))
				.isInstanceOf(UserNotActivatedException.class)
				.hasMessage("USER_BANNED");
	}
}

