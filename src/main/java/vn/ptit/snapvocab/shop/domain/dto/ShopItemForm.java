package vn.ptit.snapvocab.shop.domain.dto;

import java.time.Instant;

/**
 * Admin-editable ShopItem fields (shop.md §8). Body of create; PATCH sends any subset plus {@code version}.
 * {@code type} and {@code themeKey} are strings so an unknown value maps to a domain error code, not a parse error.
 * Server-managed fields (status, publishedAt, version on create, timestamps) are rejected, never ignored.
 */
public record ShopItemForm(String code, String type, String name, String description, String iconKey,
		Boolean purchasable, Long price, Instant availableFrom, Instant availableUntil, Integer sortOrder,
		String themeKey, String frameAssetKey, Integer boostDurationMinutes, Integer maxQuantity) {
}
