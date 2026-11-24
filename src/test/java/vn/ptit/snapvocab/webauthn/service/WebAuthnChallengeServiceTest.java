package vn.ptit.snapvocab.webauthn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;

@ExtendWith(MockitoExtension.class)
class WebAuthnChallengeServiceTest {

	@Mock
	private RedissonClient redissonClient;
	@Mock
	private RBucket<String> bucket;

	private WebAuthnChallengeService service;

	@BeforeEach
	void setUp() {
		WebAuthnProperties properties = new WebAuthnProperties();
		properties.setChallengeTtlSeconds(300L);
		service = new WebAuthnChallengeService(redissonClient, properties);
	}

	@Test
	void createRegistrationChallengeStores32ByteBase64UrlChallengeInRedis() {
		when(redissonClient.<String>getBucket("webauthn:reg:42")).thenReturn(bucket);

		String challenge = service.createRegistrationChallenge(42L);

		assertThat(challenge).isNotBlank();
		assertThat(Base64.getUrlDecoder().decode(challenge)).hasSize(32);
		verify(bucket).set(eq(challenge), eq(300L), eq(TimeUnit.SECONDS));
	}

	@Test
	void consumeRegistrationChallengeAtomicallyGetsAndDeletes() {
		when(redissonClient.<String>getBucket("webauthn:reg:42")).thenReturn(bucket);
		when(bucket.getAndDelete()).thenReturn("stored-challenge");

		String challenge = service.consumeRegistrationChallenge(42L);

		assertThat(challenge).isEqualTo("stored-challenge");
		verify(bucket).getAndDelete();
	}

	@Test
	void consumeRegistrationChallengeRejectsMissingOrExpiredChallenge() {
		when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
		when(bucket.getAndDelete()).thenReturn(null);

		assertThatThrownBy(() -> service.consumeRegistrationChallenge(42L))
				.isInstanceOf(BadRequestAlertException.class)
				.hasMessageContaining("expired");
	}

	@Test
	void createAndConsumeLoginChallengeUsesSingleUseRedisKey() {
		when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);

		WebAuthnChallengeService.LoginChallenge created = service.createLoginChallenge();

		assertThat(created.challengeId()).isNotBlank();
		assertThat(Base64.getUrlDecoder().decode(created.challenge())).hasSize(32);
		verify(bucket).set(eq(created.challenge()), eq(300L), eq(TimeUnit.SECONDS));

		when(bucket.getAndDelete()).thenReturn(created.challenge());
		assertThat(service.consumeLoginChallenge(created.challengeId())).isEqualTo(created.challenge());
	}
}

