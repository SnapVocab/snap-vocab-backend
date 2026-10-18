package vn.ptit.snapvocab.shop.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.shop.domain.CoinTransaction;
import vn.ptit.snapvocab.shop.domain.dto.WalletDTO;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.CoinTransactionRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
public class WalletService {

	private static final int MAX_PAGE_SIZE = 100;

	private final UserRepository userRepository;
	private final CoinTransactionRepository ledger;

	/** Balance is {@code users.coin}; history is the ledger, newest first, id-cursor paginated. */
	@Transactional(readOnly = true)
	public WalletDTO getWallet(Long userId, Long cursor, int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ShopException(ShopErrorCode.VALIDATION_ERROR, "1 <= size <= " + MAX_PAGE_SIZE);
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
		List<CoinTransaction> rows = ledger.findPage(userId, cursor, PageRequest.of(0, size + 1));
		List<CoinTransaction> page = rows.subList(0, Math.min(size, rows.size()));
		Long nextCursor = rows.size() > size ? page.get(page.size() - 1).getId() : null;
		return new WalletDTO(user.getCoin(),
				page.stream()
						.map(t -> new WalletDTO.Transaction(t.getId(), t.getAmount(), t.getBalanceAfter(),
								t.getSourceType(), t.getReferenceId(), t.getDescription(), t.getCreatedAt()))
						.toList(),
				nextCursor);
	}

	/**
	 * The single write path for coin: moves {@code users.coin} and appends the ledger row together.
	 * Caller must hold the user row lock ({@link Economy#lockUser}) in the current transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public CoinTransaction apply(User lockedUser, long delta, CoinSourceType source, String referenceId,
			String description, String eventKey) {
		long balance = lockedUser.getCoin() + delta;
		if (balance < 0) {
			throw new ShopException(ShopErrorCode.INSUFFICIENT_COINS, "Not enough coins",
					Map.of("balance", lockedUser.getCoin(), "required", -delta));
		}
		lockedUser.setCoin(balance);
		return ledger.save(CoinTransaction.builder()
				.user(lockedUser)
				.amount(delta)
				.balanceAfter(balance)
				.sourceType(source)
				.referenceId(referenceId)
				.description(description)
				.eventKey(eventKey)
				.build());
	}
}
