package vn.ptit.snapvocab.shop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;

@Service
@RequiredArgsConstructor
public class ShopRewardService {

	private final UserRepository userRepository;
	private final ShopItemRepository shopItemRepository;
	private final UserItemRepository userItemRepository;
	private final WalletService walletService;

	public record RewardGrant(int grantedQuantity, long fallbackCoin) {
	}

	/**
	 * Grants a reward item by its immutable code (shop.md §7.5). Units that cannot be granted for a business
	 * reason (cosmetic already owned, booster at maxQuantity, item not reward-eligible) become
	 * {@code fallbackCoin} each, recorded on the ledger as {@code {eventKey}:FALLBACK}. Technical failures propagate
	 * and roll the claim back; they never fall back to coin.
	 * <p>
	 * Must run inside the mission/chest claim transaction: that claim record is the idempotency anchor for the item
	 * part (shop.md §3.5); the fallback ledger row is additionally protected by UNIQUE event_key.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RewardGrant grantShopItemReward(Long userId, String itemCode, int quantity, long fallbackCoin,
			CoinSourceType source, String eventKey) {
		if (quantity < 1 || fallbackCoin < 0 || source == null || source == CoinSourceType.SHOP_PURCHASE) {
			throw new IllegalArgumentException("quantity >= 1, fallbackCoin >= 0 and a reward source are required");
		}
		User user = Economy.lockUser(userRepository, userId);
		ShopItem item = shopItemRepository.findByCode(itemCode)
				.orElseThrow(() -> new ShopException(ShopErrorCode.SHOP_ITEM_NOT_FOUND, "Reward item " + itemCode + " not found"));
		UserItem owned = userItemRepository.findByUserIdAndItemId(userId, item.getId()).orElse(null);
		int current = owned == null ? 0 : owned.getQuantity();

		int grantable;
		if (item.getStatus() == ShopItemStatus.DRAFT) {
			grantable = 0;
		} else if (!item.getType().isStackable()) {
			grantable = owned == null ? 1 : 0;
		} else {
			grantable = Math.max(0, Math.min(quantity, item.maxQuantityOrDefault() - current));
		}

		if (grantable > 0) {
			if (owned == null) {
				owned = UserItem.builder().user(user).item(item).itemType(item.getType()).quantity(grantable).build();
			} else {
				owned.setQuantity(current + grantable);
			}
			userItemRepository.saveAndFlush(owned);
		}
		long coin = (long) (quantity - grantable) * fallbackCoin;
		if (coin > 0) {
			walletService.apply(user, coin, source, item.getCode(), item.getName(), eventKey + ":FALLBACK");
		}
		return new RewardGrant(grantable, coin);
	}
}
