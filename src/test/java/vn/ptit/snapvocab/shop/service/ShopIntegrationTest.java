package vn.ptit.snapvocab.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.dto.ActivationResultDTO;
import vn.ptit.snapvocab.shop.domain.dto.EquippedDTO;
import vn.ptit.snapvocab.shop.domain.dto.InventoryDTO;
import vn.ptit.snapvocab.shop.domain.dto.PurchaseResultDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopEvents;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemDTO;
import vn.ptit.snapvocab.shop.domain.dto.WalletDTO;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;
import vn.ptit.snapvocab.xp.domain.ExperienceLog;
import vn.ptit.snapvocab.xp.domain.XpSourceType;
import vn.ptit.snapvocab.xp.service.XpService;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;

/** Shop / Inventory / Booster / Wallet / XP against LOCAL MySQL (real locks, constraints and generated columns). */
@LocalMySqlJpaTest
@RecordApplicationEvents
@Import({ ShopService.class, InventoryService.class, WalletService.class, ShopRewardService.class, ShopMapper.class,
		StreakFreezeService.class, XpService.class, MissionTracker.class })
class ShopIntegrationTest {

	@MockitoBean
	StorageService storageService;
	@MockitoSpyBean
	UserItemRepository userItemRepository;

	@Autowired
	ShopService shopService;
	@Autowired
	InventoryService inventoryService;
	@Autowired
	WalletService walletService;
	@Autowired
	ShopRewardService rewardService;
	@Autowired
	XpService xpService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	ShopItemRepository shopItemRepository;
	@Autowired
	DataSource dataSource;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		JdbcTemplate jdbc = jdbc();
		for (String table : List.of("streak_freeze_days", "experience_logs", "booster_activations", "coin_transactions",
				"user_items", "shop_items")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM users WHERE email LIKE 'shop-it-%'");
	}

	// --- Purchase -------------------------------------------------------------------------------

	@Test
	@DisplayName("1,3,4,5: buying a cosmetic debits coin, writes one ledger row, creates ownership, publishes event")
	void purchaseCosmetic() {
		User user = newUser(1000);
		ShopItem frame = frame(300);

		PurchaseResultDTO result = shopService.purchase(user.getId(), frame.getId(), 300L, "k1");

		assertThat(result.replayed()).isFalse();
		assertThat(result.coinBalance()).isEqualTo(700);
		assertThat(result.quantity()).isEqualTo(1);
		assertThat(coin(user)).isEqualTo(700);
		Map<String, Object> tx = jdbc().queryForMap("SELECT * FROM coin_transactions WHERE user_id = ?", user.getId());
		assertThat(tx.get("amount")).isEqualTo(-300L);
		assertThat(tx.get("balance_after")).isEqualTo(700L);
		assertThat(tx.get("source_type")).isEqualTo("SHOP_PURCHASE");
		assertThat(tx.get("reference_id")).isEqualTo(frame.getId().toString());
		assertThat(tx.get("event_key")).isEqualTo("SHOP_PURCHASE:" + user.getId() + ":k1");
		assertThat(quantity(user, frame)).isEqualTo(1);
		assertThat(events.stream(ShopEvents.ShopItemPurchased.class)).singleElement()
				.satisfies(e -> assertThat(e.type()).isEqualTo(ShopItemType.AVATAR_FRAME));
	}

	@Test
	@DisplayName("2,6: buying a booster twice stacks quantity")
	void purchaseBoosterStacks() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);

		shopService.purchase(user.getId(), booster.getId(), 100L, "a");
		PurchaseResultDTO second = shopService.purchase(user.getId(), booster.getId(), 100L, "b");

		assertThat(second.quantity()).isEqualTo(2);
		assertThat(coin(user)).isEqualTo(800);
		assertThat(ledgerRows(user)).isEqualTo(2);
	}

	@Test
	@DisplayName("7: buying an owned cosmetic is rejected and charges nothing")
	void cosmeticAlreadyOwned() {
		User user = newUser(1000);
		ShopItem frame = frame(300);
		shopService.purchase(user.getId(), frame.getId(), 300L, "a");

		assertShopError(() -> shopService.purchase(user.getId(), frame.getId(), 300L, "b"),
				ShopErrorCode.ITEM_ALREADY_OWNED);
		assertThat(coin(user)).isEqualTo(700);
	}

	@Test
	@DisplayName("8: insufficient coins")
	void insufficientCoins() {
		User user = newUser(50);
		ShopItem frame = frame(300);

		assertShopError(() -> shopService.purchase(user.getId(), frame.getId(), 300L, "a"),
				ShopErrorCode.INSUFFICIENT_COINS);
		assertThat(coin(user)).isEqualTo(50);
		assertThat(ledgerRows(user)).isZero();
	}

	@Test
	@DisplayName("9: stale expectedPrice → PRICE_CHANGED with currentPrice")
	void priceChanged() {
		User user = newUser(1000);
		ShopItem frame = frame(300);

		assertThatThrownBy(() -> shopService.purchase(user.getId(), frame.getId(), 250L, "a"))
				.isInstanceOfSatisfying(ShopException.class, e -> {
					assertThat(e.getCode()).isEqualTo(ShopErrorCode.PRICE_CHANGED);
					assertThat(e.getDetails()).isEqualTo(Map.of("currentPrice", 300L));
				});
		assertThat(coin(user)).isEqualTo(1000);
	}

	@Test
	@DisplayName("10: DRAFT is invisible to learners → SHOP_ITEM_NOT_FOUND")
	void draftItem() {
		User user = newUser(1000);
		ShopItem item = save(base(ShopItemType.AVATAR_FRAME, 100).status(ShopItemStatus.DRAFT).frameAssetKey("f"));

		assertShopError(() -> shopService.purchase(user.getId(), item.getId(), 100L, "a"),
				ShopErrorCode.SHOP_ITEM_NOT_FOUND);
		assertShopError(() -> shopService.purchase(user.getId(), 999_999L, 100L, "b"),
				ShopErrorCode.SHOP_ITEM_NOT_FOUND);
	}

	@Test
	@DisplayName("11: ARCHIVED or not purchasable → ITEM_NOT_AVAILABLE")
	void archivedItem() {
		User user = newUser(1000);
		ShopItem archived = save(base(ShopItemType.AVATAR_FRAME, 100).status(ShopItemStatus.ARCHIVED).frameAssetKey("f"));
		ShopItem rewardOnly = save(base(ShopItemType.AVATAR_FRAME, 100).purchasable(false).frameAssetKey("f"));

		assertShopError(() -> shopService.purchase(user.getId(), archived.getId(), 100L, "a"),
				ShopErrorCode.ITEM_NOT_AVAILABLE);
		assertShopError(() -> shopService.purchase(user.getId(), rewardOnly.getId(), 100L, "b"),
				ShopErrorCode.ITEM_NOT_AVAILABLE);
	}

	@Test
	@DisplayName("12: outside the sale window → ITEM_NOT_AVAILABLE")
	void outsideSaleWindow() {
		User user = newUser(1000);
		Instant now = Instant.now();
		ShopItem notYet = save(base(ShopItemType.AVATAR_FRAME, 100).frameAssetKey("f")
				.availableFrom(now.plus(Duration.ofDays(1))));
		ShopItem over = save(base(ShopItemType.AVATAR_FRAME, 100).frameAssetKey("f")
				.availableUntil(now.minus(Duration.ofMinutes(1))));

		assertShopError(() -> shopService.purchase(user.getId(), notYet.getId(), 100L, "a"),
				ShopErrorCode.ITEM_NOT_AVAILABLE);
		assertShopError(() -> shopService.purchase(user.getId(), over.getId(), 100L, "b"),
				ShopErrorCode.ITEM_NOT_AVAILABLE);
	}

	@Test
	@DisplayName("13: booster at maxQuantity → MAX_QUANTITY_REACHED")
	void maxQuantity() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 2);
		shopService.purchase(user.getId(), booster.getId(), 100L, "a");
		shopService.purchase(user.getId(), booster.getId(), 100L, "b");

		assertShopError(() -> shopService.purchase(user.getId(), booster.getId(), 100L, "c"),
				ShopErrorCode.MAX_QUANTITY_REACHED);
		assertThat(coin(user)).isEqualTo(800);
	}

	@Test
	@DisplayName("14: retrying the same Idempotency-Key replays without charging again")
	void replay() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);

		PurchaseResultDTO first = shopService.purchase(user.getId(), booster.getId(), 100L, "same");
		PurchaseResultDTO again = shopService.purchase(user.getId(), booster.getId(), 100L, "same");

		assertThat(again.replayed()).isTrue();
		assertThat(again.transactionId()).isEqualTo(first.transactionId());
		assertThat(again.quantity()).isEqualTo(1);
		assertThat(coin(user)).isEqualTo(900);
		assertThat(ledgerRows(user)).isEqualTo(1);
		assertThat(events.stream(ShopEvents.ShopItemPurchased.class)).hasSize(1);
	}

	@Test
	@DisplayName("15: same key for a different item → IDEMPOTENCY_KEY_REUSED; missing key → REQUIRED")
	void keyReuse() {
		User user = newUser(1000);
		ShopItem a = booster(100, 5);
		ShopItem b = frame(100);
		shopService.purchase(user.getId(), a.getId(), 100L, "k");

		assertShopError(() -> shopService.purchase(user.getId(), b.getId(), 100L, "k"),
				ShopErrorCode.IDEMPOTENCY_KEY_REUSED);
		assertShopError(() -> shopService.purchase(user.getId(), b.getId(), 100L, " "),
				ShopErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		assertThat(coin(user)).isEqualTo(900);
	}

	@Test
	@DisplayName("16a: concurrent purchases whose total exceeds the balance → exactly one succeeds, never negative")
	void concurrentDifferentKeys() throws Exception {
		User user = newUser(500);
		ShopItem a = frame(300);
		ShopItem b = frame(300);

		List<Object> outcomes = runConcurrently(
				() -> shopService.purchase(user.getId(), a.getId(), 300L, "x"),
				() -> shopService.purchase(user.getId(), b.getId(), 300L, "y"));

		assertThat(outcomes).filteredOn(PurchaseResultDTO.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(o -> o instanceof ShopException e
				&& e.getCode() == ShopErrorCode.INSUFFICIENT_COINS).hasSize(1);
		assertThat(coin(user)).isEqualTo(200);
	}

	@Test
	@DisplayName("16b: N concurrent requests with the same key charge once; the rest are replays")
	void concurrentSameKey() throws Exception {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		List<Callable<Object>> calls = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			calls.add(() -> shopService.purchase(user.getId(), booster.getId(), 100L, "dup"));
		}

		List<Object> outcomes = runConcurrently(calls.toArray(Callable[]::new));

		assertThat(outcomes).allMatch(PurchaseResultDTO.class::isInstance);
		assertThat(outcomes).filteredOn(o -> !((PurchaseResultDTO) o).replayed()).hasSize(1);
		assertThat(coin(user)).isEqualTo(900);
		assertThat(quantity(user, booster)).isEqualTo(1);
	}

	@Test
	@DisplayName("17: a failure after the debit rolls back coin and ledger")
	void rollbackOnGrantFailure() {
		User user = newUser(1000);
		ShopItem frame = frame(300);
		doThrow(new IllegalStateException("boom")).when(userItemRepository).saveAndFlush(any());

		assertThatThrownBy(() -> shopService.purchase(user.getId(), frame.getId(), 300L, "a"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(coin(user)).isEqualTo(1000);
		assertThat(ledgerRows(user)).isZero();
		assertThat(events.stream(ShopEvents.ShopItemPurchased.class)).isEmpty();
	}

	// --- Catalog --------------------------------------------------------------------------------

	@Test
	@DisplayName("catalog: only on-sale items, sorted, with server-computed ownership and blockReason")
	void catalog() {
		User user = newUser(150);
		ShopItem owned = save(base(ShopItemType.AVATAR_FRAME, 100).frameAssetKey("f").sortOrder(2));
		ShopItem expensive = save(base(ShopItemType.AVATAR_FRAME, 500).frameAssetKey("g").sortOrder(1));
		ShopItem booster = save(base(ShopItemType.XP_BOOSTER, 100).boostDurationMinutes(30).maxQuantity(5).sortOrder(3));
		save(base(ShopItemType.AVATAR_FRAME, 100).frameAssetKey("f").status(ShopItemStatus.DRAFT));
		save(base(ShopItemType.AVATAR_FRAME, 100).frameAssetKey("f").purchasable(false));
		shopService.purchase(user.getId(), owned.getId(), 100L, "a");

		List<ShopItemDTO> items = shopService.getCatalog(user.getId(), null);

		assertThat(items).extracting(ShopItemDTO::itemId)
				.containsExactly(expensive.getId(), owned.getId(), booster.getId());
		assertThat(items.get(0).blockReason()).isEqualTo(ShopItemDTO.BlockReason.INSUFFICIENT_COINS);
		assertThat(items.get(1).owned()).isTrue();
		assertThat(items.get(1).blockReason()).isEqualTo(ShopItemDTO.BlockReason.ALREADY_OWNED);
		assertThat(items.get(2).canPurchase()).isFalse(); // 50 coin left
		assertThat(items.get(2).config().multiplier()).isEqualTo(2);
		assertThat(shopService.getCatalog(user.getId(), "XP_BOOSTER")).singleElement()
				.satisfies(i -> assertThat(i.itemId()).isEqualTo(booster.getId()));
		assertShopError(() -> shopService.getCatalog(user.getId(), "GEMS"), ShopErrorCode.VALIDATION_ERROR);
	}

	// --- Equip ----------------------------------------------------------------------------------

	@Test
	@DisplayName("18,19,23: equip frame; equipping B unequips A; re-equip is a no-op")
	void equipFrames() {
		User user = newUser(1000);
		ShopItem a = frame(100);
		ShopItem b = frame(100);
		shopService.purchase(user.getId(), a.getId(), 100L, "a");
		shopService.purchase(user.getId(), b.getId(), 100L, "b");

		assertThat(inventoryService.equip(user.getId(), "AVATAR_FRAME", a.getId()).item().equipped()).isTrue();
		inventoryService.equip(user.getId(), "AVATAR_FRAME", b.getId());
		inventoryService.equip(user.getId(), "AVATAR_FRAME", b.getId());

		assertThat(equipped(user, a)).isFalse();
		assertThat(equipped(user, b)).isTrue();
		when(storageService.createDownloadUrl("public/shop/frame.png")).thenReturn("https://cdn/frame.png");
		assertThat(inventoryService.getEquipped(user.getId()).avatarFrameUrl()).isEqualTo("https://cdn/frame.png");
	}

	@Test
	@DisplayName("20,21: cannot equip unowned items, boosters, or the wrong slot")
	void equipRejections() {
		User user = newUser(1000);
		ShopItem frame = frame(100);
		ShopItem booster = booster(100, 5);
		shopService.purchase(user.getId(), booster.getId(), 100L, "a");

		assertShopError(() -> inventoryService.equip(user.getId(), "AVATAR_FRAME", frame.getId()), ShopErrorCode.ITEM_NOT_OWNED);
		assertShopError(() -> inventoryService.equip(user.getId(), "XP_BOOSTER", booster.getId()),
				ShopErrorCode.INVALID_EQUIPMENT_SLOT);
		assertShopError(() -> inventoryService.equip(user.getId(), "AVATAR_FRAME", booster.getId()),
				ShopErrorCode.ITEM_NOT_EQUIPPABLE);
	}

	@Test
	@DisplayName("card frame: its own slot next to the avatar frame, both on /api/users/me")
	void cardFrameHasItsOwnSlot() {
		User user = newUser(1000);
		ShopItem avatar = frame(100);
		ShopItem card = save(base(ShopItemType.CARD_FRAME, 500).frameAssetKey("public/shop/card.png"));
		shopService.purchase(user.getId(), avatar.getId(), 100L, "a");
		shopService.purchase(user.getId(), card.getId(), 500L, "c");
		assertShopError(() -> shopService.purchase(user.getId(), card.getId(), 500L, "c2"), ShopErrorCode.ITEM_ALREADY_OWNED);

		inventoryService.equip(user.getId(), "AVATAR_FRAME", avatar.getId());
		inventoryService.equip(user.getId(), "CARD_FRAME", card.getId());
		assertShopError(() -> inventoryService.equip(user.getId(), "AVATAR_FRAME", card.getId()),
				ShopErrorCode.ITEM_NOT_EQUIPPABLE);
		when(storageService.createDownloadUrl("public/shop/frame.png")).thenReturn("https://cdn/frame.png");
		when(storageService.createDownloadUrl("public/shop/card.png")).thenReturn("https://cdn/card.png");
		assertThat(inventoryService.getEquipped(user.getId()))
				.extracting(EquippedDTO::avatarFrameUrl, EquippedDTO::cardFrameUrl)
				.containsExactly("https://cdn/frame.png", "https://cdn/card.png");

		inventoryService.unequip(user.getId(), "CARD_FRAME");
		assertThat(equipped(user, card)).isFalse();
		assertThat(equipped(user, avatar)).isTrue();
		assertThat(inventoryService.getEquipped(user.getId()).cardFrameUrl()).isNull();
	}

	@Test
	@DisplayName("22: unequip clears the slot; unequipping an empty slot succeeds")
	void unequip() {
		User user = newUser(1000);
		ShopItem frame = frame(100);
		shopService.purchase(user.getId(), frame.getId(), 100L, "a");
		inventoryService.equip(user.getId(), "AVATAR_FRAME", frame.getId());

		assertThat(inventoryService.unequip(user.getId(), "AVATAR_FRAME").item()).isNull();
		assertThat(equipped(user, frame)).isFalse();
		assertThat(inventoryService.unequip(user.getId(), "AVATAR_FRAME").item()).isNull();
	}

	@Test
	@DisplayName("24: the DB itself rejects two equipped items in one slot")
	void dbEnforcesOneEquippedPerSlot() {
		User user = newUser(1000);
		ShopItem a = frame(100);
		ShopItem b = frame(100);
		shopService.purchase(user.getId(), a.getId(), 100L, "a");
		shopService.purchase(user.getId(), b.getId(), 100L, "b");

		assertThatThrownBy(() -> jdbc().update("UPDATE user_items SET equipped = 1 WHERE user_id = ?", user.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Booster --------------------------------------------------------------------------------

	@Test
	@DisplayName("25,26: activation consumes one unit and sets expiresAt = startedAt + configured duration")
	void activate() {
		User user = newUser(1000);
		ShopItem booster = save(base(ShopItemType.XP_BOOSTER, 100).boostDurationMinutes(45).maxQuantity(5));
		shopService.purchase(user.getId(), booster.getId(), 100L, "a");
		shopService.purchase(user.getId(), booster.getId(), 100L, "b");
		Instant before = Instant.now();

		ActivationResultDTO result = inventoryService.activate(user.getId(), booster.getId(), "act");

		assertThat(result.quantity()).isEqualTo(1);
		assertThat(result.replayed()).isFalse();
		assertThat(Duration.between(result.activation().startedAt(), result.activation().expiresAt()))
				.isEqualTo(Duration.ofMinutes(45));
		assertThat(result.activation().startedAt()).isBetween(before.minusSeconds(1), Instant.now());
		assertThat(quantity(user, booster)).isEqualTo(1);
		assertThat(events.stream(ShopEvents.BoosterUsed.class)).hasSize(1);
	}

	@Test
	@DisplayName("27: retrying activation with the same key does not consume again")
	void activateReplay() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		shopService.purchase(user.getId(), booster.getId(), 100L, "a");
		shopService.purchase(user.getId(), booster.getId(), 100L, "b");

		ActivationResultDTO first = inventoryService.activate(user.getId(), booster.getId(), "act");
		ActivationResultDTO again = inventoryService.activate(user.getId(), booster.getId(), "act");

		assertThat(again.replayed()).isTrue();
		assertThat(again.activation().activationId()).isEqualTo(first.activation().activationId());
		assertThat(quantity(user, booster)).isEqualTo(1);
		assertThat(events.stream(ShopEvents.BoosterUsed.class)).hasSize(1);
	}

	@Test
	@DisplayName("28: a second booster while one is active is rejected and not consumed")
	void boosterAlreadyActive() {
		User user = newUser(1000);
		ShopItem a = booster(100, 5);
		ShopItem b = booster(100, 5);
		shopService.purchase(user.getId(), a.getId(), 100L, "a");
		shopService.purchase(user.getId(), b.getId(), 100L, "b");
		inventoryService.activate(user.getId(), a.getId(), "act-a");

		assertShopError(() -> inventoryService.activate(user.getId(), b.getId(), "act-b"),
				ShopErrorCode.BOOSTER_ALREADY_ACTIVE);
		assertThat(quantity(user, b)).isEqualTo(1);
	}

	@Test
	@DisplayName("29,30: expiry is server wall-clock; after expiresAt a new booster can be activated")
	void boosterExpires() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		shopService.purchase(user.getId(), booster.getId(), 100L, "a");
		shopService.purchase(user.getId(), booster.getId(), 100L, "b");
		inventoryService.activate(user.getId(), booster.getId(), "act-1");
		assertThat(inventoryService.getInventory(user.getId()).activeBooster()).isNotNull();

		expireBoosters(user);

		assertThat(inventoryService.getInventory(user.getId()).activeBooster()).isNull();
		assertThat(inventoryService.activate(user.getId(), booster.getId(), "act-2").quantity()).isZero();
	}

	@Test
	@DisplayName("activation guards: not owned, not activatable, empty")
	void activationGuards() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		ShopItem frame = frame(100);
		assertShopError(() -> inventoryService.activate(user.getId(), booster.getId(), "x"), ShopErrorCode.ITEM_NOT_OWNED);
		shopService.purchase(user.getId(), frame.getId(), 100L, "f");
		assertShopError(() -> inventoryService.activate(user.getId(), frame.getId(), "y"),
				ShopErrorCode.ITEM_NOT_ACTIVATABLE);
		shopService.purchase(user.getId(), booster.getId(), 100L, "b");
		inventoryService.activate(user.getId(), booster.getId(), "z");
		expireBoosters(user);
		assertShopError(() -> inventoryService.activate(user.getId(), booster.getId(), "w"),
				ShopErrorCode.ITEM_QUANTITY_EMPTY);
	}

	@Test
	@DisplayName("inventory: empty booster shown only while its activation is active")
	void inventoryHidesEmptyBooster() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		shopService.purchase(user.getId(), booster.getId(), 100L, "a");
		inventoryService.activate(user.getId(), booster.getId(), "act");

		InventoryDTO active = inventoryService.getInventory(user.getId());
		assertThat(active.items()).singleElement().satisfies(i -> assertThat(i.quantity()).isZero());
		assertThat(active.activeBooster().itemId()).isEqualTo(booster.getId());

		expireBoosters(user);
		assertThat(inventoryService.getInventory(user.getId()).items()).isEmpty();
	}

	// --- XP -------------------------------------------------------------------------------------

	@Test
	@DisplayName("31,32,36,37: Review and Quiz XP are doubled while active; log keeps base/bonus; users.exp gets total")
	void boostedXp() {
		User user = activeBoosterUser();

		ExperienceLog review = xpService.award(user.getId(), XpSourceType.FLASHCARD_REVIEW, 2, "review:1");
		ExperienceLog quiz = xpService.award(user.getId(), XpSourceType.QUIZ, 5, "quiz:1");

		assertThat(review.getBaseAmount()).isEqualTo(2);
		assertThat(review.getBonusAmount()).isEqualTo(2);
		assertThat(review.getAmount()).isEqualTo(4);
		assertThat(review.getBoosterActivationId()).isNotNull();
		assertThat(quiz.getAmount()).isEqualTo(10);
		assertThat(exp(user)).isEqualTo(14);
	}

	@Test
	@DisplayName("33,34: Mission, Save word and Admin XP are never boosted")
	void unboostedSources() {
		User user = activeBoosterUser();

		for (XpSourceType source : List.of(XpSourceType.MISSION_REWARD, XpSourceType.SAVE_WORD,
				XpSourceType.ADMIN_ADJUSTMENT)) {
			ExperienceLog log = xpService.award(user.getId(), source, 10, "src:" + source);
			assertThat(log.getBonusAmount()).as(source.name()).isZero();
			assertThat(log.getBoosterActivationId()).as(source.name()).isNull();
		}
		assertThat(exp(user)).isEqualTo(30);
	}

	@Test
	@DisplayName("35: booster expired before the award → no bonus")
	void expiredBoosterNoBonus() {
		User user = activeBoosterUser();
		expireBoosters(user);

		assertThat(xpService.award(user.getId(), XpSourceType.QUIZ, 5, "quiz:late").getBonusAmount()).isZero();
	}

	@Test
	@DisplayName("XP replay by eventKey adds nothing")
	void xpIdempotent() {
		User user = activeBoosterUser();
		xpService.award(user.getId(), XpSourceType.QUIZ, 5, "quiz:once");
		xpService.award(user.getId(), XpSourceType.QUIZ, 5, "quiz:once");

		assertThat(exp(user)).isEqualTo(10);
	}

	// --- Wallet & reward ------------------------------------------------------------------------

	@Test
	@DisplayName("wallet: balance from users.coin; history newest first with cursor")
	void wallet() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		for (String key : List.of("a", "b", "c")) {
			shopService.purchase(user.getId(), booster.getId(), 100L, key);
		}

		WalletDTO first = walletService.getWallet(user.getId(), null, 2);
		assertThat(first.balance()).isEqualTo(700);
		assertThat(first.transactions()).extracting(WalletDTO.Transaction::balanceAfter).containsExactly(700L, 800L);
		assertThat(first.nextCursor()).isNotNull();

		WalletDTO second = walletService.getWallet(user.getId(), first.nextCursor(), 2);
		assertThat(second.transactions()).extracting(WalletDTO.Transaction::balanceAfter).containsExactly(900L);
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	@DisplayName("reward: grants item, falls back to coin when owned or over max, and requires the claim transaction")
	void rewardGrant() {
		User user = newUser(0);
		ShopItem frame = frame(100);
		ShopItem booster = booster(100, 3);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		var granted = tx.execute(s -> rewardService.grantShopItemReward(user.getId(), frame.getCode(), 1, 50,
				CoinSourceType.MISSION_REWARD, "mission:1"));
		var duplicate = tx.execute(s -> rewardService.grantShopItemReward(user.getId(), frame.getCode(), 1, 50,
				CoinSourceType.MISSION_REWARD, "mission:2"));
		var partial = tx.execute(s -> rewardService.grantShopItemReward(user.getId(), booster.getCode(), 5, 20,
				CoinSourceType.MISSION_REWARD, "mission:4"));

		assertThat(granted.grantedQuantity()).isEqualTo(1);
		assertThat(duplicate.grantedQuantity()).isZero();
		assertThat(duplicate.fallbackCoin()).isEqualTo(50);
		assertThat(partial.grantedQuantity()).isEqualTo(3);
		assertThat(partial.fallbackCoin()).isEqualTo(40);
		assertThat(coin(user)).isEqualTo(90);
		assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM coin_transactions WHERE event_key = 'mission:2:FALLBACK'",
				Integer.class)).isEqualTo(1);
		assertThatThrownBy(() -> rewardService.grantShopItemReward(user.getId(), frame.getCode(), 1, 50,
				CoinSourceType.MISSION_REWARD, "mission:3")).isInstanceOf(IllegalTransactionStateException.class);
	}

	// --- helpers --------------------------------------------------------------------------------

	private User activeBoosterUser() {
		User user = newUser(1000);
		ShopItem booster = booster(100, 5);
		shopService.purchase(user.getId(), booster.getId(), 100L, "buy");
		inventoryService.activate(user.getId(), booster.getId(), "act");
		return user;
	}

	/** Moves the user's activations two hours into the past (DB-relative, independent of JVM/DB time zones). */
	private void expireBoosters(User user) {
		jdbc().update("UPDATE booster_activations SET started_at = started_at - INTERVAL 2 HOUR, "
				+ "expires_at = expires_at - INTERVAL 2 HOUR WHERE user_id = ?", user.getId());
	}

	@SafeVarargs
	private static List<Object> runConcurrently(Callable<Object>... calls) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(calls.length);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> call : calls) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						return call.call();
					} catch (ShopException e) {
						return e;
					}
				}));
			}
			start.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> f : futures) {
				outcomes.add(f.get(30, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			pool.shutdownNow();
		}
	}

	private static void assertShopError(ThrowingCallable call, ShopErrorCode code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ShopException.class,
				e -> assertThat(e.getCode()).isEqualTo(code));
	}

	private User newUser(long coin) {
		return userRepository.save(User.builder()
				.email("shop-it-" + UUID.randomUUID() + "@test.local")
				.password("x".repeat(60))
				.firstName("IT")
				.activated(true)
				.coin(coin)
				.build());
	}

	private ShopItem.ShopItemBuilder base(ShopItemType type, long price) {
		return ShopItem.builder()
				.code("IT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
				.type(type)
				.name(type + " item")
				.status(ShopItemStatus.PUBLISHED)
				.purchasable(true)
				.price(price);
	}

	private ShopItem save(ShopItem.ShopItemBuilder builder) {
		return shopItemRepository.save(builder.build());
	}

	private ShopItem frame(long price) {
		return save(base(ShopItemType.AVATAR_FRAME, price).frameAssetKey("public/shop/frame.png"));
	}

	private ShopItem booster(long price, int max) {
		return save(base(ShopItemType.XP_BOOSTER, price).boostDurationMinutes(30).maxQuantity(max));
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private long coin(User user) {
		return jdbc().queryForObject("SELECT coin FROM users WHERE id = ?", Long.class, user.getId());
	}

	private long exp(User user) {
		return jdbc().queryForObject("SELECT exp FROM users WHERE id = ?", Long.class, user.getId());
	}

	private int ledgerRows(User user) {
		return jdbc().queryForObject("SELECT COUNT(*) FROM coin_transactions WHERE user_id = ?", Integer.class,
				user.getId());
	}

	private int quantity(User user, ShopItem item) {
		return jdbc().queryForObject("SELECT quantity FROM user_items WHERE user_id = ? AND shop_item_id = ?",
				Integer.class, user.getId(), item.getId());
	}

	private boolean equipped(User user, ShopItem item) {
		return jdbc().queryForObject("SELECT equipped FROM user_items WHERE user_id = ? AND shop_item_id = ?",
				Boolean.class, user.getId(), item.getId());
	}
}
