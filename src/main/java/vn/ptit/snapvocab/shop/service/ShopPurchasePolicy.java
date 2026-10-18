package vn.ptit.snapvocab.shop.service;

import java.time.Instant;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemDTO.BlockReason;

/**
 * One purchase rule for the catalog flag, the purchase guard and Daily Mission eligibility
 * {@code HAS_PURCHASABLE_ITEM} (daily_mission.md §4.3), so they can never disagree.
 */
public final class ShopPurchasePolicy {

	private ShopPurchasePolicy() {
	}

	public static BlockReason blockReason(ShopItem item, int ownedQuantity, long coin) {
		if (!item.getType().isStackable() && ownedQuantity > 0) {
			return BlockReason.ALREADY_OWNED;
		}
		if (item.getType().isStackable() && ownedQuantity >= item.maxQuantityOrDefault()) {
			return BlockReason.MAX_QUANTITY_REACHED;
		}
		if (coin < item.getPrice()) {
			return BlockReason.INSUFFICIENT_COINS;
		}
		return null;
	}

	/** On sale at {@code now} and not blocked: the learner could buy it right now. */
	public static boolean canBuyNow(ShopItem item, int ownedQuantity, long coin, Instant now) {
		return item.isAvailableAt(now) && blockReason(item, ownedQuantity, coin) == null;
	}
}
