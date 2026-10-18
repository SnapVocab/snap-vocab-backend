package vn.ptit.snapvocab.shop.domain.dto;

import java.time.Instant;

public record ActivationResultDTO(InventoryDTO.ActiveBooster activation, int quantity, Instant serverNow,
		boolean replayed) {
}
