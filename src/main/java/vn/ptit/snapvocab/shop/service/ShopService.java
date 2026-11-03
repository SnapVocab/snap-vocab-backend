package vn.ptit.snapvocab.shop.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.shop.domain.CoinTransaction;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.dto.PurchaseResultDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopEvents;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemDTO.BlockReason;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.CoinTransactionRepository;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;

@Service
public class ShopService {

	private static final Logger LOG = LoggerFactory.getLogger(ShopService.class);

	private final UserRepository userRepository;
	private final ShopItemRepository shopItemRepository;
	private final UserItemRepository userItemRepository;
	private final CoinTransactionRepository ledger;
	private final WalletService walletService;
	private final ShopMapper mapper;
	private final ApplicationEventPublisher eventPublisher;
	private final EntityManager entityManager;
	private final TransactionTemplate transaction;
	private final MissionTracker missionTracker;
	private final StreakFreezeService streakFreezeService;

	public ShopService(UserRepository userRepository, ShopItemRepository shopItemRepository,
			UserItemRepository userItemRepository, CoinTransactionRepository ledger, WalletService walletService,
			ShopMapper mapper, ApplicationEventPublisher eventPublisher, EntityManager entityManager,
			PlatformTransactionManager transactionManager, MissionTracker missionTracker,
			StreakFreezeService streakFreezeService) {
		this.missionTracker = missionTracker;
		this.streakFreezeService = streakFreezeService;
		this.userRepository = userRepository;
		this.shopItemRepository = shopItemRepository;
		this.userItemRepository = userItemRepository;
		this.ledger = ledger;
		this.walletService = walletService;
		this.mapper = mapper;
		this.eventPublisher = eventPublisher;
		this.entityManager = entityManager;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	@Transactional(readOnly = true)
	public List<ShopItemDTO> getCatalog(Long userId, String type) {
		ShopItemType typeFilter = parseType(type);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
		List<UserItem> ownedItems = userItemRepository.findAllByUserId(userId);
		Map<Long, UserItem> owned = ownedItems.stream()
				.collect(Collectors.toMap(ui -> ui.getItem().getId(), Function.identity()));
		// A pending Streak Freeze settlement frees room before the next purchase (streak_freeze.md §3.2).
		Map<Long, Integer> freezeLeft = streakFreezeService.unitsLeft(user, ownedItems, StreakFreezeService.today());
		return shopItemRepository.findCatalog(Economy.now(), typeFilter).stream()
				.map(item -> toDto(item, owned.get(item.getId()), freezeLeft.get(item.getId()), user.getCoin()))
				.toList();
	}

	private ShopItemDTO toDto(ShopItem item, UserItem owned, Integer unitsLeft, long coin) {
		int quantity = unitsLeft != null ? unitsLeft : owned == null ? 0 : owned.getQuantity();
		BlockReason block = ShopPurchasePolicy.blockReason(item, quantity, coin);
		return new ShopItemDTO(item.getId(), item.getCode(), item.getType(), item.getName(), item.getDescription(),
				mapper.iconUrl(item), item.getPrice(), item.getAvailableUntil(), mapper.config(item), quantity > 0,
				quantity, owned != null && Boolean.TRUE.equals(owned.getEquipped()), block == null, block);
	}

	/**
	 * Buys one unit (shop.md §7.2). Debit, ledger row and ownership commit together or not at all.
	 * The ledger's {@code event_key} is the idempotency anchor; a replay returns current state with
	 * {@code replayed = true} and never publishes the event again.
	 */
	public PurchaseResultDTO purchase(Long userId, Long itemId, Long expectedPrice, String idempotencyKey) {
		String key = Economy.requireKey(idempotencyKey);
		if (expectedPrice == null) {
			throw new ShopException(ShopErrorCode.VALIDATION_ERROR, "expectedPrice is required");
		}
		String eventKey = "SHOP_PURCHASE:" + userId + ":" + key;
		Purchase purchase;
		try {
			purchase = transaction.execute(status -> doPurchase(userId, itemId, expectedPrice, eventKey));
		} catch (DataIntegrityViolationException e) {
			// Last line of defence (UNIQUE event_key): re-evaluate against committed state, where the
			// winning request's ledger row turns this one into a replay. Drop the rolled-back entities first.
			LOG.info("Purchase {} hit a constraint, re-evaluating: {}", eventKey, e.getMostSpecificCause().getMessage());
			entityManager.clear();
			purchase = transaction.execute(status -> doPurchase(userId, itemId, expectedPrice, eventKey));
		}
		if (!purchase.result().replayed()) {
			eventPublisher.publishEvent(new ShopEvents.ShopItemPurchased(userId, itemId, purchase.type(),
					purchase.result().transactionId()));
		}
		return purchase.result();
	}

	private record Purchase(PurchaseResultDTO result, ShopItemType type) {
	}

	private Purchase doPurchase(Long userId, Long itemId, long expectedPrice, String eventKey) {
		User user = Economy.lockUser(userRepository, userId);
		CoinTransaction prior = ledger.findByEventKey(eventKey).orElse(null);
		if (prior != null) {
			return replay(user, prior, itemId);
		}

		ShopItem item = shopItemRepository.findById(itemId)
				.filter(i -> i.getStatus() != ShopItemStatus.DRAFT)
				.orElseThrow(() -> new ShopException(ShopErrorCode.SHOP_ITEM_NOT_FOUND, "Shop item " + itemId + " not found"));
		if (!item.isAvailableAt(Economy.now())) {
			throw new ShopException(ShopErrorCode.ITEM_NOT_AVAILABLE, "Shop item " + itemId + " is not on sale");
		}
		if (item.getPrice() != expectedPrice) {
			throw new ShopException(ShopErrorCode.PRICE_CHANGED, "Price changed", Map.of("currentPrice", item.getPrice()));
		}
		if (item.getType() == ShopItemType.STREAK_FREEZE) {
			// Settle before the units held change, so a unit bought today never covers an earlier day (§3.3).
			streakFreezeService.settle(user, StreakFreezeService.today());
		}
		UserItem owned = userItemRepository.findByUserIdAndItemId(userId, itemId).orElse(null);
		BlockReason block = ShopPurchasePolicy.blockReason(item, owned == null ? 0 : owned.getQuantity(), user.getCoin());
		if (block != null) {
			throw switch (block) {
				case ALREADY_OWNED -> new ShopException(ShopErrorCode.ITEM_ALREADY_OWNED, "Item already owned");
				case MAX_QUANTITY_REACHED -> new ShopException(ShopErrorCode.MAX_QUANTITY_REACHED,
						"Max quantity reached", Map.of("maxQuantity", item.maxQuantityOrDefault()));
				case INSUFFICIENT_COINS -> new ShopException(ShopErrorCode.INSUFFICIENT_COINS, "Not enough coins",
						Map.of("balance", user.getCoin(), "required", item.getPrice()));
			};
		}
		// Daily Mission (daily_mission.md §3.2): one actionInstant after validation; assign on the pre-purchase state.
		Instant actionInstant = Economy.now();
		LocalDate missionDate = MissionTracker.missionDate(actionInstant);
		missionTracker.ensureAssigned(userId, missionDate, actionInstant);

		CoinTransaction receipt = walletService.apply(user, -item.getPrice(), CoinSourceType.SHOP_PURCHASE,
				item.getId().toString(), item.getName(), eventKey);
		if (owned == null) {
			owned = UserItem.builder().user(user).item(item).itemType(item.getType()).quantity(1).build();
		} else {
			owned.setQuantity(owned.getQuantity() + 1);
		}
		userItemRepository.saveAndFlush(owned);
		missionTracker.apply(userId, missionDate, MissionTriggerEvent.SHOP_ITEM_PURCHASED,
				"SHOP_PURCHASE:" + receipt.getId(), MissionFacts.NONE, actionInstant);
		LOG.info("User {} bought item {} for {} coin (tx {})", userId, itemId, item.getPrice(), receipt.getId());
		return new Purchase(new PurchaseResultDTO(receipt.getId(), itemId, owned.getQuantity(), true, user.getCoin(),
				false), item.getType());
	}

	private Purchase replay(User user, CoinTransaction prior, Long itemId) {
		if (prior.getSourceType() != CoinSourceType.SHOP_PURCHASE || !itemId.toString().equals(prior.getReferenceId())) {
			throw new ShopException(ShopErrorCode.IDEMPOTENCY_KEY_REUSED,
					"Idempotency-Key was already used for another request");
		}
		int quantity = userItemRepository.findByUserIdAndItemId(user.getId(), itemId).map(UserItem::getQuantity).orElse(0);
		return new Purchase(new PurchaseResultDTO(prior.getId(), itemId, quantity, quantity > 0, user.getCoin(), true),
				null);
	}

	private static ShopItemType parseType(String type) {
		if (type == null || type.isBlank()) {
			return null;
		}
		return Arrays.stream(ShopItemType.values())
				.filter(t -> t.name().equals(type))
				.findFirst()
				.orElseThrow(() -> new ShopException(ShopErrorCode.VALIDATION_ERROR, "Unknown item type: " + type,
						Map.of("allowed", ShopItemType.values())));
	}
}
