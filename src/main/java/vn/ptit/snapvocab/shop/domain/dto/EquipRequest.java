package vn.ptit.snapvocab.shop.domain.dto;

import jakarta.validation.constraints.NotNull;

public record EquipRequest(@NotNull Long itemId) {
}
