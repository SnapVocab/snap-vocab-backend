package vn.ptit.snapvocab.webauthn.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebAuthnLoginVerifyRequest(
		@NotBlank(message = "challengeId must not be blank")
		String challengeId,

		@NotBlank(message = "Credential id must not be blank")
		String id,

		@NotNull(message = "Assertion response must not be null")
		@Valid
		AssertionResponseDTO response
) {
	public record AssertionResponseDTO(
			@NotBlank(message = "clientDataJSON must not be blank")
			String clientDataJSON,

			@NotBlank(message = "authenticatorData must not be blank")
			String authenticatorData,

			@NotBlank(message = "signature must not be blank")
			String signature,

			String userHandle
	) {}
}

