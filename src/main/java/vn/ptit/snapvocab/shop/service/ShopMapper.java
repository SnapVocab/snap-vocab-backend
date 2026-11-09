package vn.ptit.snapvocab.shop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.shop.domain.BoosterActivation;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.dto.InventoryDTO;
import vn.ptit.snapvocab.shop.domain.dto.ItemConfig;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

@Component
@RequiredArgsConstructor
public class ShopMapper {

	private final StorageService storageService;

	public String iconUrl(ShopItem item) {
		return url(item.getIconKey());
	}

	public ItemConfig config(ShopItem item) {
		return switch (item.getType()) {
			case AVATAR_FRAME, CARD_FRAME -> new ItemConfig(url(item.getFrameAssetKey()), null, null, null);
			case XP_BOOSTER -> new ItemConfig(null, ShopItemType.XP_BOOSTER_MULTIPLIER,
					item.boostDurationMinutesOrDefault(), item.maxQuantityOrDefault());
			case STREAK_FREEZE -> new ItemConfig(null, null, null, item.maxQuantityOrDefault());
		};
	}

	public InventoryDTO.Item toInventoryItem(UserItem owned) {
		return toInventoryItem(owned, owned.getQuantity());
	}

	/** {@code quantity}: what the learner holds now, e.g. after a pending Streak Freeze settlement (streak_freeze.md §3.2). */
	public InventoryDTO.Item toInventoryItem(UserItem owned, int quantity) {
		ShopItem item = owned.getItem();
		return new InventoryDTO.Item(item.getId(), item.getCode(), item.getType(), item.getStatus(), item.getName(),
				iconUrl(item), quantity, Boolean.TRUE.equals(owned.getEquipped()), config(item));
	}

	public static InventoryDTO.ActiveBooster toActiveBooster(BoosterActivation activation) {
		return new InventoryDTO.ActiveBooster(activation.getId(), activation.getShopItem().getId(),
				ShopItemType.XP_BOOSTER_MULTIPLIER, activation.getStartedAt(), activation.getExpiresAt());
	}

	public String url(String key) {
		return key == null ? null : storageService.createDownloadUrl(key);
	}
}
