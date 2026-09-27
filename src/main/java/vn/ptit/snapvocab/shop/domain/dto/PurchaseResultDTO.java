package vn.ptit.snapvocab.shop.domain.dto;

/** On replay, {@code quantity} and {@code coinBalance} reflect current state (shop.md §7.1). */
public record PurchaseResultDTO(Long transactionId, Long itemId, int quantity, boolean owned, long coinBalance,
		boolean replayed) {
}
