package vn.ptit.snapvocab.webauthn.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.user.domain.dto.auth.ResponseLoginDTO;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnCredentialResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnLoginOptionsResponse.PublicKeyRequestOptions;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.AuthenticatorSelection;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.PubKeyCredParam;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.RelyingParty;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.UserEntity;
import vn.ptit.snapvocab.webauthn.service.WebAuthnAuthenticationService;
import vn.ptit.snapvocab.webauthn.service.WebAuthnRegistrationService;

@ExtendWith(MockitoExtension.class)
class WebAuthnControllerTest {

	private static final String EMAIL = "learner@example.com";

	@Mock
	private WebAuthnRegistrationService registrationService;
	@Mock
	private WebAuthnAuthenticationService authenticationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
				new WebAuthnController(registrationService, authenticationService)).build();
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(EMAIL).claim("userId", 7L).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void registerOptionsReturnsChallengeAndRp() throws Exception {
		when(registrationService.createRegistrationOptions(EMAIL)).thenReturn(
				new WebAuthnRegisterOptionsResponse(
						"challenge-123",
						new RelyingParty("localhost", "SnapVocab"),
						new UserEntity("AAAAAAAAAAc", EMAIL, "Thanh"),
						List.of(new PubKeyCredParam("public-key", -7L)),
						60000L,
						"none",
						new AuthenticatorSelection("platform", "preferred", false, "required"),
						List.of()
				)
		);

		mockMvc.perform(post("/api/auth/webauthn/register/options"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challenge").value("challenge-123"))
				.andExpect(jsonPath("$.rp.id").value("localhost"))
				.andExpect(jsonPath("$.authenticatorSelection.userVerification").value("required"));
	}

	@Test
	void registerVerifyDelegatesToService() throws Exception {
		when(registrationService.verifyRegistration(eq(EMAIL), any())).thenReturn(
				new WebAuthnCredentialResponse(
						10L,
						"cred-abc",
						"MacBook Touch ID",
						List.of("internal"),
						true,
						false,
						Instant.parse("2026-11-24T07:00:00Z"),
						null
				)
		);

		mockMvc.perform(post("/api/auth/webauthn/register/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "id": "cred-abc",
								  "response": {
								    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0",
								    "attestationObject": "o2NmbXRkbm9uZQ",
								    "transports": ["internal"]
								  },
								  "deviceName": "MacBook Touch ID"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(10))
				.andExpect(jsonPath("$.credentialId").value("cred-abc"))
				.andExpect(jsonPath("$.deviceName").value("MacBook Touch ID"));
	}

	@Test
	void registerVerifyRejectsMissingFields() throws Exception {
		mockMvc.perform(post("/api/auth/webauthn/register/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":\"\"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(registrationService);
	}

	@Test
	void listAndDeleteCredentialsWorkForAuthenticatedUser() throws Exception {
		when(registrationService.listCredentials(EMAIL)).thenReturn(List.of(
				new WebAuthnCredentialResponse(10L, "cred-abc", "Phone", List.of("internal"), false, false, null, null)
		));

		mockMvc.perform(get("/api/auth/webauthn/credentials"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(10));

		mockMvc.perform(delete("/api/auth/webauthn/credentials/10"))
				.andExpect(status().isNoContent());

		verify(registrationService).deleteCredential(EMAIL, 10L);
	}

	@Test
	void loginOptionsWorksWithAndWithoutEmailBody() throws Exception {
		when(authenticationService.createLoginOptions(any())).thenReturn(
				new WebAuthnLoginOptionsResponse(
						"cid-1",
						new PublicKeyRequestOptions("chal-1", 60000L, "localhost", List.of(), "required")
				)
		);

		mockMvc.perform(post("/api/auth/webauthn/login/options")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"learner@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challengeId").value("cid-1"))
				.andExpect(jsonPath("$.publicKey.challenge").value("chal-1"));

		mockMvc.perform(post("/api/auth/webauthn/login/options"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challengeId").value("cid-1"));
	}

	@Test
	void loginVerifyReturnsJwtTokens() throws Exception {
		when(authenticationService.verifyLogin(any())).thenReturn(
				ResponseLoginDTO.builder()
						.accessToken("access-jwt")
						.refreshToken("refresh-tok")
						.build()
		);

		mockMvc.perform(post("/api/auth/webauthn/login/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "challengeId": "cid-1",
								  "id": "cred-abc",
								  "response": {
								    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0",
								    "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAA",
								    "signature": "MEUCIQ..."
								  }
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("access-jwt"))
				.andExpect(jsonPath("$.refresh_token").value("refresh-tok"));
	}

	@Test
	void loginVerifyRejectsMissingRequiredFields() throws Exception {
		mockMvc.perform(post("/api/auth/webauthn/login/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"challengeId\":\"\"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(authenticationService);
	}
}

