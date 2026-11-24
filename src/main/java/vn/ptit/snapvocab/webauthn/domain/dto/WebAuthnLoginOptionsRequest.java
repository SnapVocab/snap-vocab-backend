package vn.ptit.snapvocab.webauthn.domain.dto;

public record WebAuthnLoginOptionsRequest(
		/** Optional saved email from last login; when provided, populates allowCredentials. */
		String email
) {}

