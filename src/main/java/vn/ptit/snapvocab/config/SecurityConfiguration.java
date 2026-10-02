package vn.ptit.snapvocab.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;

@Configuration
public class SecurityConfiguration {

	@Value("${jwt.secret}")
	private String jwtKey;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			CustomAuthenticationEntryPoint customAuthenticationEntryPoint) throws Exception {
		String[] whiteList = {
				"/api/auth/login",
				"/api/auth/register",
				"/api/auth/refresh",
				"/api/auth/activate",
				"/v3/api-docs/**",
				"/swagger-ui/**",
				"/swagger-ui.html",
				"/api/auth/verify",
				"/api/auth/resend-code",
				"/api/auth/forgot-password",
				"/api/auth/reset-password",
				"/api/auth/refresh"
		};

		http
				.csrf(c -> c.disable())
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(
						auth -> auth
								.requestMatchers(whiteList).permitAll()
								.requestMatchers(org.springframework.http.HttpMethod.GET,
										"/api/collections",
										"/api/collections/system",
										"/api/collections/public",
										"/api/collections/{collectionId:[0-9]+}",
										"/api/collections/{collectionId:[0-9]+}/topics").permitAll()
								.requestMatchers("/api/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
								.anyRequest().authenticated()
				)
				.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(customAuthenticationEntryPoint))
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(
						session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(
				getSecretKey()).macAlgorithm(SecurityUtils.JWT_ALGORITHM).build();
		return token -> {
			try {
				return jwtDecoder.decode(token);
			} catch (Exception e) {
				System.out.println("JWT error: " + e.getMessage());
				throw e;
			}
		};
	}

	/** Authorities come from the space-separated {@code auth} claim written by AuthenticationService, unprefixed. */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(SecurityUtils.AUTHORITIES_CLAIM);
		authorities.setAuthorityPrefix("");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey()));
	}


	private SecretKey getSecretKey() {
		byte[] keyBytes = Base64.from(jwtKey).decode();
		return new SecretKeySpec(keyBytes, 0, keyBytes.length,
				SecurityUtils.JWT_ALGORITHM.getName());
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
			throws Exception {
		return configuration.getAuthenticationManager();
	}

}

