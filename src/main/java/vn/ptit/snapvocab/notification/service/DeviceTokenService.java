package vn.ptit.snapvocab.notification.service;

import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.notification.domain.dto.DeviceDTO;
import vn.ptit.snapvocab.notification.domain.dto.DeviceUnregisterResultDTO;
import vn.ptit.snapvocab.notification.domain.enumeration.DevicePlatform;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationErrorCode;
import vn.ptit.snapvocab.notification.exception.NotificationException;
import vn.ptit.snapvocab.notification.repository.DeviceTokenRepository;

/** A5/A6 (notification.md §4.3). Logs show only the last 8 characters of a token (§4.5). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

	private static final Pattern EXPO_TOKEN = Pattern.compile("Expo(nent)?PushToken\\[[A-Za-z0-9_-]+\\]");

	private final DeviceTokenRepository deviceTokenRepository;

	/** New token, refresh of the caller's own, or a device that switched account: all end in one row owned by the caller. */
	@Transactional
	public DeviceDTO register(Long userId, String token, DevicePlatform platform) {
		if (token == null || token.length() > 255 || !EXPO_TOKEN.matcher(token).matches()) {
			throw new NotificationException(NotificationErrorCode.INVALID_DEVICE_TOKEN, "Not an Expo push token");
		}
		Optional<Long> previousOwner = deviceTokenRepository.findOwnerId(token);
		Instant now = NotificationService.now();
		deviceTokenRepository.upsert(userId, token, platform.name(), now);

		String reason = previousOwner.map(owner -> owner.equals(userId) ? "refresh" : "reassigned").orElse("new");
		if (reason.equals("reassigned")) {
			log.info("Device token …{} moved from user {} to user {}", tail(token), previousOwner.get(), userId);
		}
		Metrics.counter("device_token.registered", "reason", reason).increment();
		return new DeviceDTO(platform, now);
	}

	/** Always answers; only the caller's own token is deleted. */
	@Transactional
	public DeviceUnregisterResultDTO unregister(Long userId, String token) {
		boolean removed = token != null && deviceTokenRepository.deleteOwned(token, userId) > 0;
		if (removed) {
			Metrics.counter("device_token.removed", "reason", "unregister").increment();
		}
		return new DeviceUnregisterResultDTO(removed);
	}

	static String tail(String token) {
		return token.substring(Math.max(0, token.length() - 8));
	}
}
