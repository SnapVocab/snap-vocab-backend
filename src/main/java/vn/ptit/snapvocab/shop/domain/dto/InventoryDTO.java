package vn.ptit.snapvocab.shop.domain.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

public record InventoryDTO(Instant serverNow, ActiveBooster activeBooster, List<Item> items) {

	public record Item(Long itemId, String code, ShopItemType type, ShopItemStatus status, String name,
			String iconUrl, int quantity, boolean equipped, ItemConfig config) {
	}

	public record ActiveBooster(Long activationId, Long itemId, int multiplier, Instant startedAt,
			Instant expiresAt) {
	}
}
