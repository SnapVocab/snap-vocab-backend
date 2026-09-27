package vn.ptit.snapvocab.shop.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

/** Conventions shared by every economy mutation (shop.md §7.1). */
public final class Economy {

	private static final int MAX_KEY_LENGTH = 128;

	private Economy() {
	}

	/** Server wall-clock, truncated to the DATETIME(6) precision the DB stores. */
	public static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public static String requireKey(String key) {
		if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
			throw new ShopException(ShopErrorCode.IDEMPOTENCY_KEY_REQUIRED,
					"Idempotency-Key header (1.." + MAX_KEY_LENGTH + " chars) is required");
		}
		return key;
	}

	/** Must be the first statement of the transaction: users → user_items → ledger lock order. */
	public static User lockUser(UserRepository userRepository, Long userId) {
		return userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
	}
}
