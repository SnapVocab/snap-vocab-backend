package vn.ptit.snapvocab.webauthn.domain.dto;

import java.time.Instant;
import java.util.List;

public record WebAuthnCredentialResponse(
		Long id,
		String credentialId,
		String deviceName,
		List<String> transports,
		boolean backupEligible,
		boolean backupState,
		Instant createdAt,
		Instant lastUsedAt
) {}

