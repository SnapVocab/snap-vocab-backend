package vn.ptit.snapvocab.webauthn.domain.dto;

import java.util.List;

public record WebAuthnRegisterOptionsResponse(
		String challenge,
		RelyingParty rp,
		UserEntity user,
		List<PubKeyCredParam> pubKeyCredParams,
		long timeout,
		String attestation,
		AuthenticatorSelection authenticatorSelection,
		List<PublicKeyCredentialDescriptorDTO> excludeCredentials
) {
	public record RelyingParty(String id, String name) {}

	public record UserEntity(String id, String name, String displayName) {}

	public record PubKeyCredParam(String type, long alg) {}

	public record AuthenticatorSelection(
			String authenticatorAttachment,
			String residentKey,
			boolean requireResidentKey,
			String userVerification
	) {}

	public record PublicKeyCredentialDescriptorDTO(
			String type,
			String id,
			List<String> transports
	) {}
}

