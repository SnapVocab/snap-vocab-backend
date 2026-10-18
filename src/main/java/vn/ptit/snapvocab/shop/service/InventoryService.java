package vn.ptit.snapvocab.shop.service;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.shop.domain.BoosterActivation;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.dto.ActivationResultDTO;
import vn.ptit.snapvocab.shop.domain.dto.EquipmentDTO;
import vn.ptit.snapvocab.shop.domain.dto.InventoryDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopEvents;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.BoosterActivationRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;

@Service
public class InventoryService {

	private static final Logger LOG = LoggerFactory.getLogger(InventoryService.class);

	private final UserRepository userRepository;
	private final UserItemRepository userItemRepository;
	private final BoosterActivationRepository activationRepository;
	private final ShopMapper mapper;
	private final ApplicationEventPublisher eventPublisher;
	private final EntityManager entityManager;
	private final TransactionTemplate transaction;
	private final MissionTracker missionTracker;

	public InventoryService(UserRepository userRepository, UserItemRepository userItemRepository,
			BoosterActivationRepository activationRepository, ShopMapper mapper,
			ApplicationEventPublisher eventPublisher, EntityManager entityManager,
			PlatformTransactionManager transactionManager, MissionTracker missionTracker) {
		this.missionTracker = missionTracker;
		this.userRepository = userRepository;
		this.userItemRepository = userItemRepository;
		this.activationRepository = activationRepository;
		this.mapper = mapper;
		this.eventPublisher = eventPublisher;
		this.entityManager = entityManager;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	/** Owned items; an empty booster stays hidden unless it is the one currently active (shop.md §4.2). */
	@Transactional(readOnly = true)
	public InventoryDTO getInventory(Long userId) {
		Instant now = Economy.now();
		BoosterActivation active = findActive(userId, now);
		Long activeItemId = active == null ? null : active.getShopItem().getId();
		List<InventoryDTO.Item> items = userItemRepository.findAllByUserId(userId).stream()
				.filter(ui -> ui.getQuantity() > 0 || Boolean.TRUE.equals(ui.getEquipped())
						|| ui.getItem().getId().equals(activeItemId))
				.map(mapper::toInventoryItem)
				.toList();
		return new InventoryDTO(now, active == null ? null : ShopMapper.toActiveBooster(active), items);
	}

	/** Naturally idempotent: equipping the equipped item is a no-op (shop.md §7.4). */
	@Transactional
	public EquipmentDTO equip(Long userId, String slotName, Long itemId) {
		ShopItemType slot = parseSlot(slotName);
		if (itemId == null) {
			throw new ShopException(ShopErrorCode.VALIDATION_ERROR, "itemId is required");
		}
		Economy.lockUser(userRepository, userId);
		UserItem target = userItemRepository.findByUserIdAndItemId(userId, itemId).orElseThrow(() -> notOwned(itemId));
		if (target.getItemType() != slot) {
			throw new ShopException(ShopErrorCode.ITEM_NOT_EQUIPPABLE,
					"Item " + itemId + " is " + target.getItemType() + ", not " + slot);
		}
		if (!Boolean.TRUE.equals(target.getEquipped())) {
			// Unequip first and flush, otherwise UNIQUE(user_id, equipped_slot) sees two equipped rows.
			userItemRepository.findByUserIdAndItemTypeAndEquippedTrue(userId, slot).ifPresent(current -> {
				current.setEquipped(false);
				userItemRepository.saveAndFlush(current);
			});
			target.setEquipped(true);
			userItemRepository.saveAndFlush(target);
		}
		return new EquipmentDTO(slot, mapper.toInventoryItem(target));
	}

	/** Succeeds even when nothing is equipped. */
	@Transactional
	public EquipmentDTO unequip(Long userId, String slotName) {
		ShopItemType slot = parseSlot(slotName);
		Economy.lockUser(userRepository, userId);
		userItemRepository.findByUserIdAndItemTypeAndEquippedTrue(userId, slot).ifPresent(ui -> ui.setEquipped(false));
		return new EquipmentDTO(slot, null);
	}

	/**
	 * Activates one XP Booster (shop.md §7.3). The activation row is the idempotency anchor
	 * (UNIQUE user_id + idempotency_key); a rejected activation consumes nothing and stores no key.
	 */
	public ActivationResultDTO activate(Long userId, Long itemId, String idempotencyKey) {
		String key = Economy.requireKey(idempotencyKey);
		ActivationResultDTO result;
		try {
			result = transaction.execute(status -> doActivate(userId, itemId, key));
		} catch (DataIntegrityViolationException e) {
			LOG.info("Activation {}:{} hit a constraint, re-evaluating: {}", userId, key,
					e.getMostSpecificCause().getMessage());
			entityManager.clear();
			result = transaction.execute(status -> doActivate(userId, itemId, key));
		}
		if (!result.replayed()) {
			eventPublisher.publishEvent(new ShopEvents.BoosterUsed(userId, itemId, result.activation().activationId()));
		}
		return result;
	}

	private ActivationResultDTO doActivate(Long userId, Long itemId, String key) {
		User user = Economy.lockUser(userRepository, userId);
		Instant now = Economy.now();
		BoosterActivation prior = activationRepository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
		if (prior != null) {
			if (!prior.getShopItem().getId().equals(itemId)) {
				throw new ShopException(ShopErrorCode.IDEMPOTENCY_KEY_REUSED,
						"Idempotency-Key was already used for another item");
			}
			int quantity = userItemRepository.findByUserIdAndItemId(userId, itemId).map(UserItem::getQuantity).orElse(0);
			return new ActivationResultDTO(ShopMapper.toActiveBooster(prior), quantity, now, true);
		}

		UserItem owned = userItemRepository.findByUserIdAndItemId(userId, itemId).orElseThrow(() -> notOwned(itemId));
		if (!owned.getItemType().isActivatable()) {
			throw new ShopException(ShopErrorCode.ITEM_NOT_ACTIVATABLE, "Item " + itemId + " cannot be activated");
		}
		if (owned.getQuantity() < 1) {
			throw new ShopException(ShopErrorCode.ITEM_QUANTITY_EMPTY, "No booster left for item " + itemId);
		}
		BoosterActivation active = findActive(userId, now);
		if (active != null) {
			throw new ShopException(ShopErrorCode.BOOSTER_ALREADY_ACTIVE, "An XP Booster is already active",
					Map.of("expiresAt", active.getExpiresAt()));
		}

		// Daily Mission (daily_mission.md §3.2): one actionInstant after validation; assign on the pre-activation state.
		Instant actionInstant = Economy.now();
		LocalDate missionDate = MissionTracker.missionDate(actionInstant);
		missionTracker.ensureAssigned(userId, missionDate, actionInstant);

		owned.setQuantity(owned.getQuantity() - 1);
		BoosterActivation activation = activationRepository.save(BoosterActivation.builder()
				.user(user)
				.shopItem(owned.getItem())
				.startedAt(actionInstant)
				.expiresAt(actionInstant.plus(Duration.ofMinutes(owned.getItem().boostDurationMinutesOrDefault())))
				.idempotencyKey(key)
				.build());
		userItemRepository.saveAndFlush(owned);
		missionTracker.apply(userId, missionDate, MissionTriggerEvent.BOOSTER_USED, "BOOSTER_USED:" + activation.getId(),
				MissionFacts.NONE, actionInstant);
		LOG.info("User {} activated booster item {} until {}", userId, itemId, activation.getExpiresAt());
		return new ActivationResultDTO(ShopMapper.toActiveBooster(activation), owned.getQuantity(), actionInstant, false);
	}

	private BoosterActivation findActive(Long userId, Instant now) {
		return activationRepository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now).orElse(null);
	}

	private static ShopItemType parseSlot(String slot) {
		for (ShopItemType type : ShopItemType.values()) {
			if (type.isEquippable() && type.name().equals(slot)) {
				return type;
			}
		}
		throw new ShopException(ShopErrorCode.INVALID_EQUIPMENT_SLOT, "Unknown equipment slot: " + slot,
				Map.of("allowed", List.of(ShopItemType.THEME, ShopItemType.AVATAR_FRAME)));
	}

	private static ShopException notOwned(Long itemId) {
		return new ShopException(ShopErrorCode.ITEM_NOT_OWNED, "Item " + itemId + " is not owned");
	}
}
