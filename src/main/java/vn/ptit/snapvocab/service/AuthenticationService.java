package vn.ptit.snapvocab.service;

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
import vn.ptit.snapvocab.domain.Authority;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.ForgotPasswordRequest;
import vn.ptit.snapvocab.domain.dto.RequestLoginDTO;
import vn.ptit.snapvocab.domain.dto.RequestRegisterDTO;
import vn.ptit.snapvocab.domain.dto.ResendCodeRequest;
import vn.ptit.snapvocab.domain.dto.ResetPasswordRequest;
import vn.ptit.snapvocab.domain.dto.ResponseLoginDTO;
import vn.ptit.snapvocab.domain.dto.ResponseRegisterDTO;
import vn.ptit.snapvocab.domain.dto.VerifyMailRequest;
import vn.ptit.snapvocab.domain.mapper.UserMapper;
import vn.ptit.snapvocab.repository.AuthorityRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.DomainUserDetailsService.UserWithId;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.util.constant.OtpPurpose;
import vn.ptit.snapvocab.util.exception.BadRequestAlertException;
import vn.ptit.snapvocab.util.exception.ResourceAlreadyExistException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
	private final RefreshTokenService refreshTokenService;
	private final OTPService otpService;
	private final UserRepository userRepository;

	private final AuthenticationManagerBuilder authenticationManagerBuilder;
	private final UserMapper userMapper;
	private final JwtEncoder jwtEncoder;
	private final AuthorityRepository authorityRepository;
	private final MailService mailService;
	private final PasswordEncoder passwordEncoder;
	@Value("${jwt.access-expiration:90000}")
	private long accessTokenValidityInSeconds;

	public ResponseLoginDTO login(RequestLoginDTO requestLoginDTO) {
		LOG.info("Login attempt email={}", requestLoginDTO.getEmail());

		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
				requestLoginDTO.getEmail(),
				requestLoginDTO.getPassword()
		);

		Authentication authentication = authenticationManagerBuilder.getObject()
				.authenticate(authenticationToken);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		Optional<User> userOptional = this.userRepository.findOneByEmailIgnoreCase(
				requestLoginDTO.getEmail());

		if (userOptional.isEmpty()) {
			throw new BadRequestAlertException(
					"User with username " + requestLoginDTO.getEmail() + " was not found");
		}

		return generateAuthResponse(userOptional.get());
	}

	private ResponseLoginDTO generateAuthResponse(User user) {
		Collection<GrantedAuthority> authorities = user.getAuthorities().stream()
				.map(auth -> new SimpleGrantedAuthority(
						auth.getName())).collect(Collectors.toUnmodifiableList());
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				(Object) user.getEmail(),
				null,
				authorities
		);

		String accessToken = createToken(authentication);
		String refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

		return ResponseLoginDTO.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.userDTO(userMapper.toDto(user))
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
		LOG.debug("Register for user with email {}", requestRegisterDTO.getEmail());

		if (userRepository.existsByEmail(requestRegisterDTO.getEmail())) {
			throw new ResourceAlreadyExistException(
					"User with email " + requestRegisterDTO.getEmail() + " already existed");
		}

		Authority userAuthority = authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow();

		User u = User.builder()
				.exp(0L)
				.coin(0L)
				.streakDays(0)
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
				.email(createdUser.getLastName())
				.exp(createdUser.getExp())
				.coin(createdUser.getCoin())
				.streakDays(createdUser.getStreakDays())
				.build();

		LOG.debug("Sending verification code to email {} with TTL = 5", createdUser.getEmail());
		String code = otpService.saveOtp(createdUser.getEmail(), OtpPurpose.SIGNUP_VERIFY);
		LOG.debug("Sending code {} to email {}", code, createdUser.getEmail());
		try {
			mailService.sendVerificationEmail(createdUser.getEmail(), code, Duration.ofMinutes(5));
		} catch (MessagingException e) {
			LOG.debug("Send verification email error {}", e.getMessage());
		}

		return responseRegisterDTO;
	}

	@Transactional
	public ResponseLoginDTO verifyCode(VerifyMailRequest verifyMailRequest) {
		boolean valid = this.otpService.verifyOtp(verifyMailRequest.getEmail(),
				verifyMailRequest.getCode(), OtpPurpose.SIGNUP_VERIFY);

		if (!valid) {
			throw new BadRequestAlertException("Verify code is invalid or has expired.");
		}

		User u = this.userRepository.findOneByEmailIgnoreCase(verifyMailRequest.getEmail()).orElseThrow(
				() -> new UsernameNotFoundException("User " + verifyMailRequest.getEmail() + " not found")
		);

		u.setActivated(true);
		this.userRepository.save(u);

		return this.generateAuthResponse(u);
	}

	public void resendCode(ResendCodeRequest resendCodeRequest) {
		User u = this.userRepository.findOneByEmailIgnoreCase(resendCodeRequest.email()).orElseThrow(
				() -> new UsernameNotFoundException("User " + resendCodeRequest.email() + " not found")
		);

		if (u.isActivated()) {
			throw new BadRequestAlertException("User " + resendCodeRequest.email() + " was activated");
		}

		String code = otpService.saveOtp(resendCodeRequest.email(), OtpPurpose.SIGNUP_VERIFY);

		try {
			this.mailService.sendVerificationEmail(resendCodeRequest.email(), code,
					Duration.ofMinutes(5));
		} catch (MessagingException e) {
			LOG.debug("Failed to send code {} to email {}", code, resendCodeRequest.email());
		}
	}

	public void forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
		LOG.debug("Forgot password for email={}", forgotPasswordRequest.email());

		User u = userRepository.findOneByEmailIgnoreCase(forgotPasswordRequest.email())
				.orElseThrow(() -> new UsernameNotFoundException(
						"User " + forgotPasswordRequest.email() + " not found"));

		if (!u.isActivated()) {
			throw new BadRequestAlertException(
					"User " + forgotPasswordRequest.email() + " has not been activated");
		}

		String code = otpService.saveOtp(forgotPasswordRequest.email(), OtpPurpose.FORGOT_PASSWORD);

		try {
			mailService.sendPasswordResetEmail(forgotPasswordRequest.email(), code,
					Duration.ofMinutes(5));
		} catch (MessagingException e) {
			LOG.debug("Failed to send password reset email to {}: {}", forgotPasswordRequest.email(),
					e.getMessage());
		}
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
		LOG.debug("Reset password for email={}", resetPasswordRequest.email());

		boolean valid = otpService.verifyOtp(resetPasswordRequest.email(),
				resetPasswordRequest.code(), OtpPurpose.FORGOT_PASSWORD);

		if (!valid) {
			throw new BadRequestAlertException("Reset code is invalid or has expired.");
		}

		User u = userRepository.findOneByEmailIgnoreCase(resetPasswordRequest.email())
				.orElseThrow(() -> new UsernameNotFoundException(
						"User " + resetPasswordRequest.email() + " not found"));

		u.setPassword(passwordEncoder.encode(resetPasswordRequest.newPassword()));
		userRepository.save(u);
	}
}
