package vn.ptit.snapvocab.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RequestLoginDTO {

	@Email
	@NonNull
	private String email;

	@NonNull
	@Size(min = 5, max = 60)
	private String password;
}
