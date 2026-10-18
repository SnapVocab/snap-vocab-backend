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
			case THEME -> new ItemConfig(item.getThemeKey(), null, null, null, null);
			case AVATAR_FRAME -> new ItemConfig(null, url(item.getFrameAssetKey()), null, null, null);
			case XP_BOOSTER -> new ItemConfig(null, null, ShopItemType.XP_BOOSTER_MULTIPLIER,
					item.boostDurationMinutesOrDefault(), item.maxQuantityOrDefault());
		};
	}

	public InventoryDTO.Item toInventoryItem(UserItem owned) {
		ShopItem item = owned.getItem();
		return new InventoryDTO.Item(item.getId(), item.getCode(), item.getType(), item.getStatus(), item.getName(),
				iconUrl(item), owned.getQuantity(), Boolean.TRUE.equals(owned.getEquipped()), config(item));
	}

	public static InventoryDTO.ActiveBooster toActiveBooster(BoosterActivation activation) {
		return new InventoryDTO.ActiveBooster(activation.getId(), activation.getShopItem().getId(),
				ShopItemType.XP_BOOSTER_MULTIPLIER, activation.getStartedAt(), activation.getExpiresAt());
	}

	String url(String key) {
		return key == null ? null : storageService.createDownloadUrl(key);
	}
}
