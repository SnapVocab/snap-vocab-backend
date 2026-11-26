package vn.ptit.snapvocab.user.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@Email(message = "email is invalid")
		@NotBlank(message = "email must not be blank")
		String email,

		@Size(min = 6, max = 6, message = "code length must be equal to 6")
		@NotBlank(message = "code must not be blank")
		String code,

		// Same rule as sign-up and change-password (5-60): the app checks one rule everywhere.
		@Size(min = 5, max = 60, message = "password must be between 5 and 60 characters")
		@NotBlank(message = "newPassword must not be blank")
		String newPassword
) {
}

