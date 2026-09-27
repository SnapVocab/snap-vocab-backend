package vn.ptit.snapvocab.shop.domain.dto;

import java.time.Instant;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/** Catalog entry with the caller's ownership state; {@code canPurchase}/{@code blockReason} are computed server-side. */
public record ShopItemDTO(Long itemId, String code, ShopItemType type, String name, String description,
		String iconUrl, Long price, Instant availableUntil, ItemConfig config, boolean owned, int quantity,
		boolean equipped, boolean canPurchase, BlockReason blockReason) {

	public enum BlockReason {
		ALREADY_OWNED,
		MAX_QUANTITY_REACHED,
		INSUFFICIENT_COINS
	}
}
