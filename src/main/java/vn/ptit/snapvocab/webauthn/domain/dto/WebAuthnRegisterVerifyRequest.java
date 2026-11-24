package vn.ptit.snapvocab.webauthn.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WebAuthnRegisterVerifyRequest(
		@NotBlank(message = "Credential id must not be blank")
		String id,

		@NotNull(message = "Attestation response must not be null")
		@Valid
		AttestationResponseDTO response,

		@Size(max = 120, message = "Device name must not exceed 120 characters")
		String deviceName
) {
	public record AttestationResponseDTO(
			@NotBlank(message = "clientDataJSON must not be blank")
			String clientDataJSON,

			@NotBlank(message = "attestationObject must not be blank")
			String attestationObject,

			List<String> transports
	) {}
}

