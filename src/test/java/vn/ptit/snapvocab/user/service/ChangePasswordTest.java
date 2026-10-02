package vn.ptit.snapvocab.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ptit.snapvocab.user.domain.Authority;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.user.domain.dto.user.ChangePasswordRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.user.domain.mapper.UserMapper;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;

/** Change password from Settings (MH-PROFILE-03): other devices are signed out. */
@ExtendWith(MockitoExtension.class)
class ChangePasswordTest {

	private static final String EMAIL = "learner@example.com";

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private RefreshTokenService refreshTokenService;
	@Mock
	private SecurityAuditLogger securityAuditLogger;
	@Mock
	private UserMapper userMapper;
	@Mock
	private JwtEncoder jwtEncoder;
	@Mock
	private AvatarUrlService avatarUrlService;

	@InjectMocks
	private AuthenticationService service;

	private User user;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "accessTokenValidityInSeconds", 900L);
		Authority authority = new Authority();
		authority.setName("ROLE_USER");
		user = User.builder()
				.id(7L)
				.email(EMAIL)
				.password("stored-hash")
				.authorities(Set.of(authority))
				.build();
		lenient().when(userRepository.findOneWithAuthoritiesByEmailIgnoreCase(EMAIL))
				.thenReturn(Optional.of(user));
	}

	private ChangePasswordRequest request() {
		return new ChangePasswordRequest("old-pass", "new-pass");
	}

	@Test
	void wrongCurrentPasswordIsRejected() {
		when(passwordEncoder.matches("old-pass", "stored-hash")).thenReturn(false);

		assertThatThrownBy(() -> service.changePassword(EMAIL, request()))
				.isInstanceOf(BadRequestAlertException.class);
		verify(userRepository, never()).save(any());
		verify(refreshTokenService, never()).revokeAllTokens(any());
		verify(securityAuditLogger).failure("CHANGE_PASSWORD", EMAIL, "INVALID_CURRENT_PASSWORD");
	}

	@Test
	void reusingTheSamePasswordIsRejected() {
		when(passwordEncoder.matches("old-pass", "stored-hash")).thenReturn(true);
		when(passwordEncoder.matches("new-pass", "stored-hash")).thenReturn(true);

		assertThatThrownBy(() -> service.changePassword(EMAIL, request()))
				.isInstanceOf(BadRequestAlertException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void successStoresTheHashSignsOutOthersAndKeepsThisDeviceSignedIn() {
		when(passwordEncoder.matches("old-pass", "stored-hash")).thenReturn(true);
		when(passwordEncoder.matches("new-pass", "stored-hash")).thenReturn(false);
		when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");
		when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(userMapper.toDto(user)).thenReturn(UserDTO.builder().id(7L).build());
		when(refreshTokenService.createRefreshToken(EMAIL)).thenReturn("fresh-refresh");
		when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("fresh-access")
				.header("alg", "HS512").subject(EMAIL)
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build());

		ResponseLoginDTO response = service.changePassword(EMAIL, request());

		assertThat(user.getPassword()).isEqualTo("new-hash");
		// Every refresh token of this account is dropped, then this device gets a new one.
		verify(refreshTokenService).revokeAllTokens(EMAIL);
		assertThat(response.getAccessToken()).isEqualTo("fresh-access");
		assertThat(response.getRefreshToken()).isEqualTo("fresh-refresh");
		verify(securityAuditLogger).success("CHANGE_PASSWORD", 7L);
	}

	@Test
	void logoutRevokesOnlyThatRefreshToken() {
		service.logout("device-token");

		verify(refreshTokenService).revokeToken("device-token");
		verify(refreshTokenService, never()).revokeAllTokens(any());
	}
}

