package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;

class ScanWebhookVerifierTest {

	private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
	private static final long NOW_EPOCH = NOW.getEpochSecond();
	private static final String SECRET = "super-webhook-secret";
	private static final String BODY = "{\"request_id\":\"req-1\",\"status\":\"DONE\"}";

	private AiServiceProperties properties;
	private ScanWebhookVerifier verifier;

	@BeforeEach
	void setUp() {
		properties = new AiServiceProperties();
		properties.setWebhookSecret(SECRET);
		properties.setWebhookToleranceSeconds(300);
		verifier = new ScanWebhookVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void verifyAcceptsValidSignatureWithinTolerance() {
		String sig = ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, BODY);

		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), sig, BODY)).isTrue();
	}

	@Test
	void verifyAcceptsSha256PrefixAndUppercaseHex() {
		String sig = "sha256=" + ScanWebhookVerifier.sign(SECRET, NOW_EPOCH - 120, BODY).toUpperCase(Locale.ROOT);

		assertThat(verifier.verify(String.valueOf(NOW_EPOCH - 120), sig, BODY)).isTrue();
	}

	@Test
	void verifyFallsBackToServiceTokenWhenWebhookSecretBlank() {
		properties.setWebhookSecret("  ");
		properties.setServiceToken("service-tok");
		String sig = ScanWebhookVerifier.sign("service-tok", NOW_EPOCH, BODY);

		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), sig, BODY)).isTrue();
	}

	@Test
	void verifyRejectsWhenNoSecretConfigured() {
		properties.setWebhookSecret("");
		properties.setServiceToken(null);
		String sig = ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, BODY);

		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), sig, BODY)).isFalse();
	}

	@Test
	void verifyRejectsTamperedBodyOrWrongSecret() {
		String sig = ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, BODY);

		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), sig, BODY + " ")).isFalse();
		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), ScanWebhookVerifier.sign("wrong", NOW_EPOCH, BODY), BODY))
				.isFalse();
	}

	@Test
	void verifyRejectsExpiredOrFutureTimestampOutsideTolerance() {
		long tooOld = NOW_EPOCH - 301;
		long tooNew = NOW_EPOCH + 301;

		assertThat(verifier.verify(String.valueOf(tooOld), ScanWebhookVerifier.sign(SECRET, tooOld, BODY), BODY))
				.isFalse();
		assertThat(verifier.verify(String.valueOf(tooNew), ScanWebhookVerifier.sign(SECRET, tooNew, BODY), BODY))
				.isFalse();
	}

	@Test
	void verifyRejectsMalformedOrMissingHeaders() {
		String sig = ScanWebhookVerifier.sign(SECRET, NOW_EPOCH, BODY);

		assertThat(verifier.verify("not-a-number", sig, BODY)).isFalse();
		assertThat(verifier.verify(null, sig, BODY)).isFalse();
		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), null, BODY)).isFalse();
		assertThat(verifier.verify(String.valueOf(NOW_EPOCH), sig, null)).isFalse();
	}
}

