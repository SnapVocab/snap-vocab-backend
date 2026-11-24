package vn.ptit.snapvocab.webauthn.service;

import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;

final class WebAuthnSupport {

	private WebAuthnSupport() {}

	static String encodeBase64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static byte[] decodeBase64Url(String value, String fieldName) {
		try {
			return Base64.getUrlDecoder().decode(value);
		} catch (IllegalArgumentException e) {
			throw new BadRequestAlertException("Invalid Base64URL encoding for " + fieldName);
		}
	}

	static String encodeUserHandle(Long userId) {
		byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(userId).array();
		return encodeBase64Url(bytes);
	}

	static Set<String> normalizeTransports(List<String> transports) {
		if (transports == null || transports.isEmpty()) {
			return Collections.emptySet();
		}
		return transports.stream()
				.filter(t -> t != null && !t.isBlank())
				.map(String::trim)
				.collect(Collectors.toSet());
	}

	static String serializeTransports(Set<String> transports) {
		if (transports == null || transports.isEmpty()) {
			return null;
		}
		return transports.stream().sorted().collect(Collectors.joining(","));
	}

	static List<String> parseTransports(String serialized) {
		if (serialized == null || serialized.isBlank()) {
			return List.of();
		}
		return Arrays.stream(serialized.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	static ServerProperty buildServerProperty(WebAuthnProperties properties, String expectedChallenge) {
		Set<Origin> origins = properties.getAllowedOrigins().stream()
				.filter(o -> o != null && !o.isBlank())
				.map(Origin::new)
				.collect(Collectors.toSet());

		return new ServerProperty(
				origins,
				properties.getRpId(),
				new DefaultChallenge(decodeBase64Url(expectedChallenge, "challenge"))
		);
	}

	static String userVerificationRequirement(WebAuthnProperties properties) {
		return properties.isRequireUserVerification() ? "required" : "preferred";
	}
}

