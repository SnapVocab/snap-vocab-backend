package vn.ptit.snapvocab.shop.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

/** Conventions shared by every economy mutation (shop.md §7.1). */
public final class Economy {

	/**
	 * Approved Daily Mission Coin per learner per day (PD-DM-02, PO 2026-10-22). Same value as the admin's
	 * MAX_COIN_PER_DAY; a catalog edit that pushes the ceiling past it needs a reason (admin_audit_log_backend.md §4.5.2).
	 */
	public static final int APPROVED_DAILY_COIN = 75;

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
