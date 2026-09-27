package vn.ptit.snapvocab.shop.domain.dto;

import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/** Slot state after equip/unequip; {@code item} is null when the slot falls back to the default. */
public record EquipmentDTO(ShopItemType slot, InventoryDTO.Item item) {
}
