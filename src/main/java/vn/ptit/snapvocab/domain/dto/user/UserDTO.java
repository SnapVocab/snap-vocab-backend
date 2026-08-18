package vn.ptit.snapvocab.domain.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
public class UserDTO {

	private Long id;

	@Size(max = 10)
	private String nativeLanguage;

	@Size(max = 50)
	private String firstName;

	@Size(max = 50)
	private String lastName;

	@Size(max = 10)
	private String learningLanguage;

	@Size(max = 2048)
	private String avatarUrl;

	@Min(value = 0)
	private Integer exp;

	@Min(value = 0)
	private Integer coin;

	@Min(value = 0)
	private Integer streakDays;

	private Instant lastStudiedAt;

	private String email;

	private String bio;
}
