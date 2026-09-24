package vn.ptit.snapvocab.domain.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "current password must not be blank")
		String currentPassword,

		@NotBlank(message = "new password must not be blank")
		@Size(min = 5, max = 60, message = "new password must be between 5 and 60 characters")
		String newPassword
) {
}
