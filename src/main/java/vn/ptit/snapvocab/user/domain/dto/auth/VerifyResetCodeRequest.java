package vn.ptit.snapvocab.user.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/auth/verify-reset-code: checks a reset code before the new password is typed; does not consume it. */
public record VerifyResetCodeRequest(
		@Email(message = "email is invalid")
		@NotBlank(message = "email must not be blank")
		String email,

		@Size(min = 6, max = 6, message = "code length must be equal to 6")
		@NotBlank(message = "code must not be blank")
		String code
) {
}
