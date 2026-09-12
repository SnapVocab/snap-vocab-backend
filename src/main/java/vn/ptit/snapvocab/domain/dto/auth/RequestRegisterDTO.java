package vn.ptit.snapvocab.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RequestRegisterDTO {

	@NotBlank(message = "email must not be blank")
	@Email
	private String email;
	@NotBlank(message = "first name must not be blank")
	private String firstName;

	@NotBlank(message = "last name must not be blank")
	private String lastName;

	@NotBlank(message = "password must not be blank")
	@Size(min = 5, max = 60)
	private String password;
}
