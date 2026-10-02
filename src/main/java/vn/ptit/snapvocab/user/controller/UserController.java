package vn.ptit.snapvocab.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateProfileRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateUserSettingsRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.user.domain.dto.user.UserSettingsDTO;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.user.service.UserService;
import vn.ptit.snapvocab.user.service.UserSettingsService;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

/** Profile and Settings (BF-04, MH-PROFILE-01..03). A learner only ever sees their own data. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final UserSettingsService userSettingsService;

	@GetMapping("/me")
	public ResponseEntity<UserDTO> getProfile() {
		return ResponseEntity.ok(userService.getProfile(currentEmail()));
	}

	@PutMapping("/me")
	public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(userService.updateProfile(currentEmail(), request));
	}

	@GetMapping("/me/settings")
	public ResponseEntity<UserSettingsDTO> getSettings() {
		return ResponseEntity.ok(userSettingsService.get(currentUserId()));
	}

	@PutMapping("/me/settings")
	public ResponseEntity<UserSettingsDTO> updateSettings(
			@Valid @RequestBody UpdateUserSettingsRequest request) {
		return ResponseEntity.ok(userSettingsService.update(currentUserId(), request));
	}

	private String currentEmail() {
		return SecurityUtils.getCurrentUserLogin()
				.orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
	}

	private Long currentUserId() {
		return userService.getCurrentUserId()
				.orElseThrow(() -> new ResourceNotFoundException("Not authenticated"));
	}
}

