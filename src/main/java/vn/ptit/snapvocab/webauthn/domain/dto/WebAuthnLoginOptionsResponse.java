package vn.ptit.snapvocab.webauthn.domain.dto;

import java.util.List;
import vn.ptit.snapvocab.webauthn.domain.dto.WebAuthnRegisterOptionsResponse.PublicKeyCredentialDescriptorDTO;

public record WebAuthnLoginOptionsResponse(
		String challengeId,
		PublicKeyRequestOptions publicKey
) {
	public record PublicKeyRequestOptions(
			String challenge,
			long timeout,
			String rpId,
			List<PublicKeyCredentialDescriptorDTO> allowCredentials,
			String userVerification
	) {}
}

