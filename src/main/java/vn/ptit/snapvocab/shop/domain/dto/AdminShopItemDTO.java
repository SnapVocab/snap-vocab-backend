package vn.ptit.snapvocab.shop.domain.dto;

import java.time.Instant;
import vn.ptit.snapvocab.shop.domain.enumeration.Behavior;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/**
 * Admin editor view of a ShopItem. Type config is flat and named like {@link ShopItemForm}; storage keys are
 * returned with short-lived preview URLs. {@code multiplier} is the developer constant (XP_BOOSTER only).
 */
public record AdminShopItemDTO(Long id, Long version, String code, ShopItemType type, Behavior behavior,
		String name, String description, String iconKey, String iconUrl, ShopItemStatus status,
		boolean purchasable, Long price, Instant availableFrom, Instant availableUntil, int sortOrder,
		String frameAssetKey, String frameAssetUrl, Integer boostDurationMinutes,
		Integer maxQuantity, Integer multiplier, Instant publishedAt, Instant createdAt, Instant updatedAt) {
}
