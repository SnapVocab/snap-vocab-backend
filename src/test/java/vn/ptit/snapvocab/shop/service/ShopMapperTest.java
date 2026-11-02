package vn.ptit.snapvocab.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.storage.service.StorageService;

class ShopMapperTest {

	@Test
	void streakFreezeCanBeMappedWithoutTreatingItAsAnXpBooster() {
		ShopItem item = ShopItem.builder().type(ShopItemType.valueOf("STREAK_FREEZE")).maxQuantity(5).build();
		var config = new ShopMapper(mock(StorageService.class)).config(item);
		assertThat(config.maxQuantity()).isEqualTo(5);
		assertThat(config.multiplier()).isNull();
		assertThat(config.durationMinutes()).isNull();
		assertThat(item.getType().isEquippable()).isFalse();
		assertThat(item.getType().isActivatable()).isFalse();
		assertThat(item.getType().isStackable()).isTrue();
	}
}
