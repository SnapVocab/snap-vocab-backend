package vn.ptit.snapvocab.shop.domain.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;

/** {@code nextCursor} is null on the last page. */
public record WalletDTO(long balance, List<Transaction> transactions, Long nextCursor) {

	public record Transaction(Long id, long amount, long balanceAfter, CoinSourceType sourceType,
			String referenceId, String description, Instant createdAt) {
	}
}
