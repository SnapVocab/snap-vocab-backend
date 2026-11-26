package vn.ptit.snapvocab.user.service;


import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.user.domain.Authority;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.auth.ForgotPasswordRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.RefreshTokenRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.RequestLoginDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.RequestRegisterDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.ResendCodeRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.ResetPasswordRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseRegisterDTO;
import vn.ptit.snapvocab.user.domain.dto.auth.VerifyMailRequest;
import vn.ptit.snapvocab.user.domain.dto.auth.VerifyResetCodeRequest;
import vn.ptit.snapvocab.webauthn.repository.WebAuthnCredentialRepository;
import vn.ptit.snapvocab.webauthn.service.BiometricAuthService;
import vn.ptit.snapvocab.user.domain.dto.user.ChangePasswordRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.user.domain.mapper.UserMapper;
import vn.ptit.snapvocab.user.repository.AuthorityRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.DomainUserDetailsService.UserWithId;
import vn.ptit.snapvocab.security.SecurityAuditLogger;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.constant.OtpPurpose;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.UserNotActivatedException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
	private final RefreshTokenService refreshTokenService;
	private final OTPService otpService;
	private final UserRepository userRepository;
	private final SecurityAuditLogger securityAuditLogger;

	private final AuthenticationManagerBuilder authenticationManagerBuilder;
	private final UserMapper userMapper;
	private final JwtEncoder jwtEncoder;
	private final AuthorityRepository authorityRepository;
	private final MailService mailService;
	private final PasswordEncoder passwordEncoder;
	private final AvatarUrlService avatarUrlService;
	private final WebAuthnCredentialRepository webAuthnCredentialRepository;
	@Value("${jwt.access-expiration:900}")
	private long accessTokenValidityInSeconds;

	public ResponseLoginDTO login(RequestLoginDTO requestLoginDTO) {
		LOG.info("Login attempt");

		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
				requestLoginDTO.getEmail(),
				requestLoginDTO.getPassword()
		);

		Authentication authentication;
		try {
			authentication = authenticationManagerBuilder.getObject()
					.authenticate(authenticationToken);
		} catch (AuthenticationException e) {
			securityAuditLogger.failure(
					"LOGIN", requestLoginDTO.getEmail(), "INVALID_CREDENTIALS");
			throw e;
		}
		SecurityContextHolder.getContext().setAuthentication(authentication);

		Optional<User> userOptional = this.userRepository.findOneByEmailIgnoreCase(
				requestLoginDTO.getEmail());

		if (userOptional.isEmpty()) {
			securityAuditLogger.failure(
					"LOGIN", requestLoginDTO.getEmail(), "USER_NOT_FOUND_AFTER_AUTHENTICATION");
			throw new BadRequestAlertException(
					"User with username " + requestLoginDTO.getEmail() + " was not found");
		}

		User user = userOptional.get();
		ResponseLoginDTO response = generateAuthResponse(user);
		securityAuditLogger.success("LOGIN", user.getId());
		return response;
	}

	/** Issues a fresh access + refresh token pair for an already-verified user (e.g. WebAuthn). */
	public ResponseLoginDTO issueTokensForAuthenticatedUser(User user) {
		return generateAuthResponse(user);
	}

	/** Every token pair comes from here (login, email verify, refresh, WebAuthn), so this is the one ban guard. */
	private ResponseLoginDTO generateAuthResponse(User user) {
		if (user.getBannedAt() != null) {
			securityAuditLogger.failure("TOKEN_ISSUE", user.getEmail(), "USER_BANNED");
			throw new UserNotActivatedException("USER_BANNED");
		}
		Collection<GrantedAuthority> authorities = user.getAuthorities().stream()
				.map(auth -> new SimpleGrantedAuthority(
						auth.getName())).collect(Collectors.toUnmodifiableList());
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				UserWithId.fromUser(user),
				null,
				authorities
		);

		String accessToken = createToken(authentication);
		String refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

		UserDTO userDTO = userMapper.toDto(user);
		// The stored value is a storage key; the app needs a URL it can load.
		userDTO.setAvatarUrl(avatarUrlService.resolve(user.getAvatarUrl()));

		return ResponseLoginDTO.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.userDTO(userDTO)
				.build();
	}

	public String createToken(Authentication authentication) {
		String authorities = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority).collect(
						Collectors.joining(" "));

		Instant validity = Instant.now().plus(this.accessTokenValidityInSeconds, ChronoUnit.SECONDS);

		JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
				.issuedAt(Instant.now())
				.expiresAt(validity)
				.subject(authentication.getName())
				.claim(SecurityUtils.AUTHORITIES_CLAIM, authorities);

		if (authentication.getPrincipal() instanceof UserWithId user) {
			builder.claim(SecurityUtils.USER_ID_CLAIM, user.getId());
		}

		JwsHeader jwsHeader = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, builder.build()))
				.getTokenValue();
	}

	@Transactional
	public ResponseRegisterDTO register(RequestRegisterDTO requestRegisterDTO) {
		LOG.debug("Registration attempt");

		if (userRepository.existsByEmail(requestRegisterDTO.getEmail())) {
			securityAuditLogger.failure(
					"REGISTRATION", requestRegisterDTO.getEmail(), "ACCOUNT_ALREADY_EXISTS");
			throw new ResourceAlreadyExistException(
					"User with email " + requestRegisterDTO.getEmail() + " already existed");
		}

		Authority userAuthority = authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow();

		User u = User.builder()
				.exp(0L)
				.coin(0L)
				.streakDays(1)
				.activated(false)
				.email(requestRegisterDTO.getEmail())
				.authorities(Set.of(userAuthority))
				.firstName(requestRegisterDTO.getFirstName())
				.lastName(requestRegisterDTO.getLastName())
				.password(passwordEncoder.encode(requestRegisterDTO.getPassword()))
				.build();

		User createdUser = userRepository.save(u);

		ResponseRegisterDTO responseRegisterDTO = ResponseRegisterDTO.builder()
				.isActivated(createdUser.isActivated())
				.lastName(createdUser.getLastName())
				.firstName(createdUser.getFirstName())
				.email(createdUser.getEmail())
				.exp(createdUser.getExp())
				.coin(createdUser.getCoin())
				.streakDays(createdUser.getStreakDays())
				.createdAt(createdUser.getCreatedAt())
				.build();

		LOG.debug("Creating registration verification code");
		String code = otpService.saveOtp(createdUser.getEmail(), OtpPurpose.SIGNUP_VERIFY);
		try {
			mailService.sendVerificationEmail(createdUser.getEmail(), code, Duration.ofMinutes(5));
		} catch (MessagingException e) {
			LOG.warn("Failed to send registration verification email: {}", e.getMessage());
			securityAuditLogger.failure(
					"REGISTRATION_EMAIL_DELIVERY", createdUser.getEmail(), "EMAIL_DELIVERY_FAILED");
		}

		securityAuditLogger.success("REGISTRATION", createdUser.getId());

		return responseRegisterDTO;
	}

	@Transactional
	public ResponseLoginDTO verifyCode(VerifyMailRequest verifyMailRequest) {
		boolean valid = this.otpService.verifyOtp(verifyMailRequest.getEmail(),
				verifyMailRequest.getCode(), OtpPurpose.SIGNUP_VERIFY);

		if (!valid) {
			securityAuditLogger.failure(
					"EMAIL_VERIFICATION", verifyMailRequest.getEmail(), "INVALID_OR_EXPIRED_CODE");
			throw new BadRequestAlertException("Verify code is invalid or has expired.");
		}

		User u = this.userRepository.findOneByEmailIgnoreCase(verifyMailRequest.getEmail())
				.orElseThrow(() -> {
					securityAuditLogger.failure(
							"EMAIL_VERIFICATION", verifyMailRequest.getEmail(), "USER_NOT_FOUND");
					return new UsernameNotFoundException(
							"User " + verifyMailRequest.getEmail() + " not found");
				});

		u.setActivated(true);
		this.userRepository.save(u);

		ResponseLoginDTO response = this.generateAuthResponse(u);
		securityAuditLogger.success("EMAIL_VERIFICATION", u.getId());
		return response;
	}

	public void resendCode(ResendCodeRequest resendCodeRequest) {
		User u = this.userRepository.findOneByEmailIgnoreCase(resendCodeRequest.email())
				.orElseThrow(() -> {
					securityAuditLogger.failure(
							"VERIFICATION_CODE_RESEND", resendCodeRequest.email(), "USER_NOT_FOUND");
					return new UsernameNotFoundException(
							"User " + resendCodeRequest.email() + " not found");
				});

		if (u.isActivated()) {
			securityAuditLogger.failure(
					"VERIFICATION_CODE_RESEND", u.getEmail(), "ACCOUNT_ALREADY_ACTIVATED");
			throw new BadRequestAlertException("User " + resendCodeRequest.email() + " was activated");
		}

		String code = otpService.saveOtp(resendCodeRequest.email(), OtpPurpose.SIGNUP_VERIFY);

		try {
			this.mailService.sendVerificationEmail(resendCodeRequest.email(), code,
					Duration.ofMinutes(5));
		} catch (MessagingException e) {
			LOG.warn("Failed to resend registration verification email: {}", e.getMessage());
			securityAuditLogger.failure(
					"VERIFICATION_CODE_EMAIL_DELIVERY", u.getEmail(), "EMAIL_DELIVERY_FAILED");
		}

		securityAuditLogger.success("VERIFICATION_CODE_RESEND", u.getId());
	}

	public void forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
		LOG.debug("Forgot-password request");

		User u = userRepository.findOneByEmailIgnoreCase(forgotPasswordRequest.email())
				.orElseThrow(() -> {
					securityAuditLogger.failure(
							"PASSWORD_RESET_REQUEST", forgotPasswordRequest.email(), "USER_NOT_FOUND");
					return new UsernameNotFoundException(
							"User " + forgotPasswordRequest.email() + " not found");
				});

		if (!u.isActivated()) {
			securityAuditLogger.failure(
					"PASSWORD_RESET_REQUEST", u.getEmail(), "ACCOUNT_NOT_ACTIVATED");
			throw new BadRequestAlertException(
					"User " + forgotPasswordRequest.email() + " has not been activated");
		}

		String code = otpService.saveOtp(forgotPasswordRequest.email(), OtpPurpose.FORGOT_PASSWORD);

		try {
			mailService.sendPasswordResetEmail(forgotPasswordRequest.email(), code,
					Duration.ofMinutes(5));
		} catch (MessagingException e) {
			LOG.warn("Failed to send password reset email: {}", e.getMessage());
			securityAuditLogger.failure(
					"PASSWORD_RESET_EMAIL_DELIVERY", u.getEmail(), "EMAIL_DELIVERY_FAILED");
		}

		securityAuditLogger.success("PASSWORD_RESET_REQUEST", u.getId());
	}

	/** Error of a reset code that is wrong, expired or burnt by too many wrong guesses. */
	public static final String INVALID_RESET_CODE = "INVALID_OR_EXPIRED_CODE";

	/** The reset screen asks this before the new password is typed; the code stays valid for resetPassword. */
	public void verifyResetCode(VerifyResetCodeRequest request) {
		if (!otpService.checkOtp(request.email(), request.code(), OtpPurpose.FORGOT_PASSWORD)) {
			securityAuditLogger.failure("PASSWORD_RESET_CODE_CHECK", request.email(), INVALID_RESET_CODE);
			throw new BadRequestAlertException(INVALID_RESET_CODE);
		}
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
		LOG.info("Reset password attempt");

		boolean valid = otpService.verifyOtp(resetPasswordRequest.email(),
				resetPasswordRequest.code(), OtpPurpose.FORGOT_PASSWORD);

		if (!valid) {
			securityAuditLogger.failure(
					"PASSWORD_RESET", resetPasswordRequest.email(), "INVALID_OR_EXPIRED_CODE");
			throw new BadRequestAlertException(INVALID_RESET_CODE);
		}

		User u = userRepository.findOneByEmailIgnoreCase(resetPasswordRequest.email())
				.orElseThrow(() -> {
					securityAuditLogger.failure(
							"PASSWORD_RESET", resetPasswordRequest.email(), "USER_NOT_FOUND");
					return new UsernameNotFoundException(
							"User " + resetPasswordRequest.email() + " not found");
				});

		u.setPassword(passwordEncoder.encode(resetPasswordRequest.newPassword()));
		userRepository.save(u);
		refreshTokenService.revokeAllTokens(u.getEmail()); // sessions opened with the old password end here
		revokeFingerprintDevices(u.getId());
		securityAuditLogger.success("PASSWORD_RESET", u.getId());
	}

	public ResponseLoginDTO refreshToken(RefreshTokenRequest refreshTokenRequest) {
		String username;
		try {
			username = this.refreshTokenService.getUsernameFromToken(
					refreshTokenRequest.refreshToken());
		} catch (BadRequestAlertException e) {
			securityAuditLogger.failure(
					"REFRESH_TOKEN_ROTATION", null, "INVALID_OR_EXPIRED_TOKEN");
			throw e;
		}

		this.refreshTokenService.revokeToken(refreshTokenRequest.refreshToken());

		User u = this.userRepository.findOneByEmailIgnoreCase(username).orElseThrow(() -> {
			securityAuditLogger.failure(
					"REFRESH_TOKEN_ROTATION", username, "USER_NOT_FOUND");
			return new UsernameNotFoundException(
					"User with email " + username + " was not found");
		});

		ResponseLoginDTO response = generateAuthResponse(u);
		securityAuditLogger.success("REFRESH_TOKEN_ROTATION", u.getId());
		return response;
	}

	/** Signs the caller out of this device only (BF-02: revoke the refresh token). */
	public void logout(String refreshToken) {
		refreshTokenService.revokeToken(refreshToken);
	}

	/**
	 * Changes the password of the signed-in user and signs every device out,
	 * then hands this device a fresh pair of tokens so the user stays signed in
	 * where they made the change.
	 */
	@Transactional
	public ResponseLoginDTO changePassword(String email, ChangePasswordRequest request) {
		User user = userRepository.findOneWithAuthoritiesByEmailIgnoreCase(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			securityAuditLogger.failure("CHANGE_PASSWORD", email, "INVALID_CURRENT_PASSWORD");
			throw new BadRequestAlertException("Current password is incorrect");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
			throw new BadRequestAlertException("New password must differ from the current one");
		}

		user.setPassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);

		refreshTokenService.revokeAllTokens(email);
		revokeFingerprintDevices(user.getId());
		ResponseLoginDTO response = generateAuthResponse(user);
		securityAuditLogger.success("CHANGE_PASSWORD", user.getId());
		return response;
	}

	/**
	 * A device token signs in without the password, so it ends with the password: someone who changes or resets it
	 * because the account may be known to others must not leave a fingerprint login open on another phone.
	 * The learner turns fingerprint sign-in on again in Settings.
	 */
	private void revokeFingerprintDevices(Long userId) {
		long revoked = webAuthnCredentialRepository.deleteByUserIdAndTransports(userId, BiometricAuthService.BIOMETRIC_TRANSPORT);
		if (revoked > 0) {
			securityAuditLogger.success("BIOMETRIC_REVOKED_ON_PASSWORD_CHANGE", userId);
		}
	}
}
