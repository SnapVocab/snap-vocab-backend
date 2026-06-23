package vn.ptit.snapvocab.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.util.exception.BadRequestAlertException;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private final String REFRESH_TOKEN_PREFIX = "refresh_token:";
	private final RedissonClient redissonClient;
	@Value("${jwt.refresh-expiration}")
	private Long refreshValidityInSeconds;

	public String createRefreshToken(String username) {
		String token = UUID.randomUUID().toString();

		redissonClient.<String>getBucket(REFRESH_TOKEN_PREFIX + token)
				.set(username, refreshValidityInSeconds, TimeUnit.SECONDS);

		return token;
	}

	public String getUsernameFromToken(String token) {
		String username = redissonClient.<String>getBucket(REFRESH_TOKEN_PREFIX + token).get();

		if (username.isEmpty()) {
			throw new BadRequestAlertException("Token invalid or expired");
		}

		return username;
	}

	public void revokeToken(String token) {
		redissonClient.<String>getBucket(REFRESH_TOKEN_PREFIX + token).delete();
	}

	public void revokeAllTokens(String username) {
		redissonClient.getKeys()
				.getKeysByPattern(REFRESH_TOKEN_PREFIX + "*")
				.forEach(key -> {
					String value = redissonClient.<String>getBucket(key).get();
					if (username.equals(value)) {
						redissonClient.getBucket(key).delete();
					}
				});
	}
}
