package vn.ptit.snapvocab.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
		@Email(message = "email is invalid")
		@NotBlank(message = "email must not be blank")
		String email
) {

}
