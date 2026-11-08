package vn.ptit.snapvocab.config;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import vn.ptit.snapvocab.security.SecurityAuditLogger;

/** Exercises the real security filter chain without starting a database or external services. */
@SpringJUnitConfig(classes = {CorsConfig.class, SecurityConfiguration.class,
		CustomAuthenticationEntryPoint.class, CorsSecurityTest.WebConfig.class})
@WebAppConfiguration
@TestPropertySource(properties = "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==")
class CorsSecurityTest {
	private static final String ORIGIN = "https://admin.snapvocab.thanhthbm.online";
	private static final String PATH = "/api/admin/mission-templates";
	@Autowired
	WebApplicationContext context;
	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@ParameterizedTest
	@ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
	void adminPreflightPassesBeforeJwt(String method) throws Exception {
		mvc.perform(options(PATH)
				.header("Origin", ORIGIN)
				.header("Access-Control-Request-Method", method)
				.header("Access-Control-Request-Headers", "Authorization, Content-Type, Accept")
				.header("Authorization", "Bearer invalid"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"))
				.andExpect(header().string("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept"));
	}

	@Test
	void actualAdminRequestStillRequiresJwtAndKeepsCorsHeaders() throws Exception {
		mvc.perform(get(PATH).header("Origin", ORIGIN))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
	}

	@Test
	void untrustedOriginIsRejected() throws Exception {
		mvc.perform(options(PATH).header("Origin", "https://untrusted.example")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Configuration
	@EnableWebMvc
	@EnableWebSecurity
	static class WebConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

		@Bean
		SecurityAuditLogger securityAuditLogger() {
			return mock(SecurityAuditLogger.class);
		}
	}
}
