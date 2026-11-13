package vn.ptit.snapvocab.shop.domain.dto;

/** Null {@code itemId} is checked in InventoryService (VALIDATION_ERROR), not by bean validation. */
public record EquipRequest(Long itemId) {
}
