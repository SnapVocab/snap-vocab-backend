package vn.ptit.snapvocab.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.RequestLoginDTO;
import vn.ptit.snapvocab.domain.dto.ResponseLoginDTO;
import vn.ptit.snapvocab.domain.mapper.UserMapper;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.DomainUserDetailsService.UserWithId;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.util.exception.BadRequestAlertException;

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

		String accessToken = this.createToken(authentication);
		String refreshToken = this.refreshTokenService.createRefreshToken(requestLoginDTO.getEmail());

		return ResponseLoginDTO.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.userDTO(userMapper.toDto(userOptional.get()))
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
}
