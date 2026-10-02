package vn.ptit.snapvocab.user.domain.dto.auth;

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
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RequestLoginDTO {

	@Email
	@NotBlank(message = "email must not be blank")
	private String email;

	@NotBlank(message = "password must not be blank")
	@Size(min = 5, max = 60)
	private String password;
}

