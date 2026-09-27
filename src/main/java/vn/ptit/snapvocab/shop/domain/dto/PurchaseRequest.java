package vn.ptit.snapvocab.shop.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code expectedPrice} only detects a stale UI; the charged price is always the server's. */
public record PurchaseRequest(@NotNull @Positive Long expectedPrice) {
}
