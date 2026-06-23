package vn.ptit.snapvocab.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseRegisterDTO {

	private String lastName;
	private String firstName;
	private String email;
	private Long exp;
	private Long coin;
	private Integer streakDays;
	private boolean isActivated;
}
