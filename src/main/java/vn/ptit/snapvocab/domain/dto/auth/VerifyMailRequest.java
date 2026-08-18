package vn.ptit.snapvocab.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VerifyMailRequest {

	@NotBlank(message = "email must not be blank")
	@Email(message = "email is invalid")
	private String email;

	@Size(min = 6, max = 6, message = "code length must be equal to 6")
	@NotBlank(message = "code must not be blank")
	private String code;
}
