package vn.ptit.snapvocab.shop.domain.dto;

/**
 * {@code expectedPrice} only detects a stale UI; the charged price is always the server's.
 * Null is checked in ShopService so the error keeps the Shop error shape (no bean validation).
 */
public record PurchaseRequest(Long expectedPrice) {
}
