package vn.ptit.snapvocab.shop.domain.dto;

import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/** Application events published only after commit and never on replay (shop.md §7.1). */
public final class ShopEvents {

	private ShopEvents() {
	}

	public record ShopItemPurchased(Long userId, Long itemId, ShopItemType type, Long transactionId) {
	}

	public record BoosterUsed(Long userId, Long itemId, Long activationId) {
	}
}
