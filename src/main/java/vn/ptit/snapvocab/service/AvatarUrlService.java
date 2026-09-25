package vn.ptit.snapvocab.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Turns the stored avatar value into something the app can load.
 *
 * <p>{@code users.avatar_url} holds the storage object key. Presigned URLs
 * expire (BF-04: TTL ≤ 15 minutes), so one is minted per response instead of
 * being stored. Rows written before that change hold a full URL; those are
 * passed through, even though an expired one no longer loads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarUrlService {

	private final StorageService storageService;

	public String resolve(String stored) {
		if (!StringUtils.hasText(stored)) {
			return null;
		}
		if (stored.startsWith("http://") || stored.startsWith("https://")) {
			return stored; // legacy row: a URL was stored directly
		}
		try {
			return storageService.createDownloadUrl(stored);
		} catch (RuntimeException e) {
			// A missing avatar must not stop the profile from loading.
			log.warn("Could not sign avatar {}: {}", stored, e.getMessage());
			return null;
		}
	}
}
