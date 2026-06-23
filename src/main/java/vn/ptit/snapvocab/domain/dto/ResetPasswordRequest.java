package vn.ptit.snapvocab.domain.dto;

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

		@Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
		@NotBlank(message = "newPassword must not be blank")
		String newPassword
) {
}
