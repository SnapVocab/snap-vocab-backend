package vn.ptit.snapvocab.user.domain.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.shop.domain.dto.EquippedDTO;
import vn.ptit.snapvocab.shop.domain.dto.InventoryDTO;

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
	private Long exp;

	@Min(value = 0)
	private Long coin;

	@Min(value = 0)
	private Integer streakDays;

	private Instant lastStudiedAt;

	private String email;

	private String bio;

	private Instant createdAt;

	private Instant updatedAt;

	/** Shop state for GET/PUT /api/users/me only (shop.md §10.1); null on other responses such as login. */
	private EquippedDTO equipped;

	private InventoryDTO.ActiveBooster activeBooster;
}

