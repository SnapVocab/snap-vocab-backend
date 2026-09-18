package vn.ptit.snapvocab.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage keys for scan uploads: {@code scans/{userId}/{uuid}.{ext}}.
 *
 * <p>The user id in the key is what lets POST /api/scan check that an objectKey
 * belongs to the caller, and the extension is how the worker knows the image
 * type without trusting a client-supplied header.
 */
public final class ScanObjectKeys {

	private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp");

	private ScanObjectKeys() {
	}

	public static boolean isSupportedType(String contentType) {
		return contentType != null && EXTENSION_BY_TYPE.containsKey(contentType.toLowerCase(Locale.ROOT));
	}

	public static String newKey(Long userId, String contentType) {
		return prefix(userId) + UUID.randomUUID() + "." + EXTENSION_BY_TYPE.get(contentType.toLowerCase(Locale.ROOT));
	}

	/**
	 * True only for a key this class generated for {@code userId}: right prefix,
	 * no path tricks, a supported extension.
	 */
	public static boolean belongsTo(String objectKey, Long userId) {
		return objectKey != null
				&& objectKey.startsWith(prefix(userId))
				&& objectKey.indexOf('/', prefix(userId).length()) < 0
				&& !objectKey.contains("..")
				&& contentTypeOf(objectKey).isPresent();
	}

	public static Optional<String> contentTypeOf(String objectKey) {
		int dot = objectKey.lastIndexOf('.');
		if (dot < 0) {
			return Optional.empty();
		}
		String ext = objectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
		return EXTENSION_BY_TYPE.entrySet().stream()
				.filter(e -> e.getValue().equals(ext))
				.map(Map.Entry::getKey)
				.findFirst();
	}

	public static String filenameOf(String objectKey) {
		return objectKey.substring(objectKey.lastIndexOf('/') + 1);
	}

	private static String prefix(Long userId) {
		return "scans/" + userId + "/";
	}
}
