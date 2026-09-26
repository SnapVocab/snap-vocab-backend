package vn.ptit.snapvocab.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateProfileRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateUserSettingsRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.user.domain.dto.user.UserSettingsDTO;
import vn.ptit.snapvocab.user.domain.enumeration.ThemePreference;
import vn.ptit.snapvocab.user.service.UserService;
import vn.ptit.snapvocab.user.service.UserSettingsService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

	private static final String EMAIL = "learner@example.com";
	private static final Long USER_ID = 7L;

	@Mock
	private UserService userService;
	@Mock
	private UserSettingsService userSettingsService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, userSettingsService)).build();
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(EMAIL).claim("userId", USER_ID).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getMeReturnsTheSignedInUser() throws Exception {
		when(userService.getProfile(EMAIL)).thenReturn(
				UserDTO.builder().id(USER_ID).email(EMAIL).avatarUrl("https://storage/signed").build());

		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.avatarUrl").value("https://storage/signed"));
	}

	@Test
	void putMeUpdatesTheProfile() throws Exception {
		when(userService.updateProfile(eq(EMAIL), any())).thenReturn(UserDTO.builder().firstName("Trọng").build());

		mockMvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
						.content("{\"firstName\":\"Trọng\",\"bio\":\"xin chào\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Trọng"));

		ArgumentCaptor<UpdateProfileRequest> request = ArgumentCaptor.forClass(UpdateProfileRequest.class);
		verify(userService).updateProfile(eq(EMAIL), request.capture());
		org.assertj.core.api.Assertions.assertThat(request.getValue().getBio()).isEqualTo("xin chào");
	}

	@Test
	void getSettingsUsesTheUserId() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
		when(userSettingsService.get(USER_ID)).thenReturn(
				new UserSettingsDTO("vi", ThemePreference.SYSTEM, 20, LocalTime.of(20, 0), true));

		mockMvc.perform(get("/api/users/me/settings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uiLanguage").value("vi"))
				.andExpect(jsonPath("$.dailyGoal").value(20))
				.andExpect(jsonPath("$.pushEnabled").value(true));
	}

	@Test
	void putSettingsPassesThePartialUpdateThrough() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
		when(userSettingsService.update(eq(USER_ID), any())).thenReturn(
				new UserSettingsDTO("en", ThemePreference.DARK, 30, null, false));

		mockMvc.perform(put("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON)
						.content("{\"uiLanguage\":\"en\",\"theme\":\"DARK\",\"dailyGoal\":30,\"srsReminderCleared\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.theme").value("DARK"));

		ArgumentCaptor<UpdateUserSettingsRequest> request = ArgumentCaptor.forClass(UpdateUserSettingsRequest.class);
		verify(userSettingsService).update(eq(USER_ID), request.capture());
		org.assertj.core.api.Assertions.assertThat(request.getValue().srsReminderCleared()).isTrue();
	}

	@Test
	void settingsRejectAnUnsupportedLanguage() throws Exception {
		mockMvc.perform(put("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON)
						.content("{\"uiLanguage\":\"fr\"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(userSettingsService);
	}

	@Test
	void settingsRejectAnOutOfRangeDailyGoal() throws Exception {
		mockMvc.perform(put("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON)
						.content("{\"dailyGoal\":0}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(userSettingsService);
	}
}

