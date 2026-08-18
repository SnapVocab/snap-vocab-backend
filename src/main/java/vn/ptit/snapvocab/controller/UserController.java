package vn.ptit.snapvocab.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.domain.dto.user.UpdateProfileRequest;
import vn.ptit.snapvocab.domain.dto.user.UserDTO;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PutMapping("/profile")
	public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		String email = SecurityUtils.getCurrentUserLogin()
				.orElseThrow(() -> new RuntimeException("Not authenticated"));
		return ResponseEntity.ok(userService.updateProfile(email, request));
	}
}
