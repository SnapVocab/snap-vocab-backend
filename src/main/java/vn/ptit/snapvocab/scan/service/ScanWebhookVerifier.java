package vn.ptit.snapvocab.scan.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;

/**
 * Verifies {@code X-Webhook-Timestamp} and {@code X-Webhook-Signature} on
 * callbacks from snap-vocab-ai using HMAC-SHA256 over {@code timestamp + "." + rawBody}.
 */
@Component
@RequiredArgsConstructor
public class ScanWebhookVerifier {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String SIGNATURE_PREFIX = "sha256=";

	private final AiServiceProperties properties;
	private final Clock clock;

	public boolean verify(String timestampHeader, String signatureHeader, String rawBody) {
		String secret = properties.effectiveWebhookSecret();
		if (!StringUtils.hasText(secret) || !StringUtils.hasText(timestampHeader)
				|| !StringUtils.hasText(signatureHeader) || rawBody == null) {
			return false;
		}

		String trimmedTimestamp = timestampHeader.trim();
		long timestamp;
		try {
			timestamp = Long.parseLong(trimmedTimestamp);
		} catch (NumberFormatException e) {
			return false;
		}

		long skewSeconds = Math.abs(clock.instant().getEpochSecond() - timestamp);
		if (skewSeconds > properties.getWebhookToleranceSeconds()) {
			return false;
		}

		String actualHex = signatureHeader.trim().toLowerCase(Locale.ROOT);
		if (actualHex.startsWith(SIGNATURE_PREFIX)) {
			actualHex = actualHex.substring(SIGNATURE_PREFIX.length());
		}

		String expectedHex = sign(secret, trimmedTimestamp, rawBody);
		return MessageDigest.isEqual(
				expectedHex.getBytes(StandardCharsets.UTF_8),
				actualHex.getBytes(StandardCharsets.UTF_8));
	}

	public static String sign(String secret, long timestamp, String rawBody) {
		return sign(secret, String.valueOf(timestamp), rawBody);
	}

	private static String sign(String secret, String timestamp, String rawBody) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(HMAC_ALGORITHM + " unavailable", e);
		}
	}
}

