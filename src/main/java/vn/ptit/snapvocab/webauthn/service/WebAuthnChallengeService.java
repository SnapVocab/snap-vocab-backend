package vn.ptit.snapvocab.webauthn.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;

@Service
@RequiredArgsConstructor
public class WebAuthnChallengeService {

	private static final String REG_CHALLENGE_PREFIX = "webauthn:reg:";
	private static final String LOGIN_CHALLENGE_PREFIX = "webauthn:login:";
	private static final int CHALLENGE_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final RedissonClient redissonClient;
	private final WebAuthnProperties properties;

	public record LoginChallenge(String challengeId, String challenge) {}

	/** Generates a 32-byte Base64URL challenge and stores it in Redis with a short TTL. */
	public String createRegistrationChallenge(Long userId) {
		String challenge = generateChallenge();

		redissonClient.<String>getBucket(REG_CHALLENGE_PREFIX + userId)
				.set(challenge, properties.getChallengeTtlSeconds(), TimeUnit.SECONDS);

		return challenge;
	}

	/** Atomically retrieves and deletes the registration challenge so it can only be used once. */
	public String consumeRegistrationChallenge(Long userId) {
		String challenge = redissonClient.<String>getBucket(REG_CHALLENGE_PREFIX + userId)
				.getAndDelete();

		if (challenge == null || challenge.isBlank()) {
			throw new BadRequestAlertException("Registration challenge is missing or expired");
		}

		return challenge;
	}

	/** Generates a single-use login challenge keyed by a random challengeId in Redis. */
	public LoginChallenge createLoginChallenge() {
		String challengeId = UUID.randomUUID().toString();
		String challenge = generateChallenge();

		redissonClient.<String>getBucket(LOGIN_CHALLENGE_PREFIX + challengeId)
				.set(challenge, properties.getChallengeTtlSeconds(), TimeUnit.SECONDS);

		return new LoginChallenge(challengeId, challenge);
	}

	/** Atomically retrieves and deletes the login challenge so it cannot be replayed. */
	public String consumeLoginChallenge(String challengeId) {
		String challenge = redissonClient.<String>getBucket(LOGIN_CHALLENGE_PREFIX + challengeId)
				.getAndDelete();

		if (challenge == null || challenge.isBlank()) {
			throw new BadRequestAlertException("Login challenge is missing or expired");
		}

		return challenge;
	}

	private String generateChallenge() {
		byte[] bytes = new byte[CHALLENGE_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}

