package vn.ptit.snapvocab.progress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.domain.dto.StreakDTO;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.UserItem;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemDTO;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.repository.UserItemRepository;
import vn.ptit.snapvocab.shop.service.InventoryService;
import vn.ptit.snapvocab.shop.service.ShopMapper;
import vn.ptit.snapvocab.shop.service.ShopRewardService;
import vn.ptit.snapvocab.shop.service.ShopService;
import vn.ptit.snapvocab.shop.service.WalletService;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/** Streak Freeze settlement through the real writers and readers, against LOCAL MySQL (streak_freeze.md §8). */
@LocalMySqlJpaTest
@Import({ ProgressService.class, StreakFreezeService.class, MissionTracker.class, ShopService.class,
		InventoryService.class, ShopRewardService.class, WalletService.class, ShopMapper.class })
class StreakFreezeIntegrationTest {

	private static final LocalDate T = LocalDate.now(ProgressService.ZONE);
	private static final int S = 5;

	@MockitoBean
	StorageService storageService;

	@Autowired
	ProgressService progressService;
	@Autowired
	ShopService shopService;
	@Autowired
	InventoryService inventoryService;
	@Autowired
	ShopRewardService rewardService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	ShopItemRepository shopItemRepository;
	@Autowired
	UserItemRepository userItemRepository;
	@Autowired
	DataSource dataSource;
	@Autowired
	PlatformTransactionManager transactionManager;

	@AfterEach
	void cleanUp() {
		for (String table : List.of("streak_freeze_days", "learning_events", "user_daily_mission_progress_logs",
				"user_daily_missions", "coin_transactions", "user_items", "shop_items")) {
			jdbc().update("DELETE FROM " + table);
		}
		jdbc().update("DELETE FROM users WHERE email LIKE 'freeze-it-%'");
	}

	@Test
	void aMissedDayTakesAUnitAndStudyingContinuesTheChain() { // §4 #2, #9 (AC-SF-01, AC-SF-02)
		User user = learner(T.minusDays(2), 0);
		ShopItem freeze = freeze(2);
		hold(user, freeze, 1);

		// Reads show the pending settlement and write nothing.
		assertThat(progressService.getStreak(user.getId()))
				.isEqualTo(new StreakDTO(S, S, T.minusDays(2), 0, T.minusDays(1)));
		assertThat(progressService.getHomeWidget(user.getId()).currentStreak()).isEqualTo(S);
		assertThat(inventoryService.getInventory(user.getId()).items()).isEmpty();
		assertThat(quantity(user, freeze)).isEqualTo(1);
		assertThat(frozenDays(user)).isEmpty();

		study(user, "a");

		assertThat(frozenDays(user)).containsExactly(T.minusDays(1));
		assertThat(quantity(user, freeze)).isZero();
		assertThat(progressService.getStreak(user.getId()))
				.isEqualTo(new StreakDTO(S + 1, S + 1, T, 0, T.minusDays(1)));
	}

	@Test
	void runningOutBreaksTheChainAndAUnitBoughtLaterNeverRepairsIt() { // §4 #3, #5 (AC-SF-03)
		User user = learner(T.minusDays(3), 1000);
		ShopItem freeze = freeze(2);
		hold(user, freeze, 1);
		assertThat(progressService.getStreak(user.getId()).currentStreak()).isZero();

		shopService.purchase(user.getId(), freeze.getId(), 200L, "buy");

		// The purchase settled first: T-2 took the held unit, T-1 broke the chain and closed it.
		assertThat(frozenDays(user)).containsExactly(T.minusDays(2));
		assertThat(streakDays(user)).isZero();
		assertThat(quantity(user, freeze)).isEqualTo(1);
		assertThat(progressService.getStreak(user.getId()).freezeCount()).isEqualTo(1);

		study(user, "b");

		assertThat(streakDays(user)).isEqualTo(1);
		assertThat(quantity(user, freeze)).isEqualTo(1);
	}

	@Test
	void catalogAndInventoryCountUnitsAfterThePendingSettlement() {
		User user = learner(T.minusDays(2), 1000);
		ShopItem freeze = freeze(2);
		hold(user, freeze, 2);

		ShopItemDTO offer = shopService.getCatalog(user.getId(), "STREAK_FREEZE").get(0);
		assertThat(offer.quantity()).isEqualTo(1);
		assertThat(offer.canPurchase()).isTrue();
		assertThat(inventoryService.getInventory(user.getId()).items()).singleElement()
				.satisfies(item -> assertThat(item.quantity()).isEqualTo(1));
		assertThat(quantity(user, freeze)).isEqualTo(2);

		// The purchase settles, so the room the catalog promised is there.
		assertThat(shopService.purchase(user.getId(), freeze.getId(), 200L, "room").quantity()).isEqualTo(2);
	}

	@Test
	void aRewardGrantSettlesFirstThenFallsBackToCoinAtTheLimit() { // AC-SF-07
		User user = learner(T.minusDays(2), 0);
		ShopItem freeze = freeze(2);
		hold(user, freeze, 2);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		var first = tx.execute(s -> rewardService.grantShopItemReward(user.getId(), freeze.getCode(), 1, 10,
				CoinSourceType.MISSION_REWARD, "mission:1"));
		var second = tx.execute(s -> rewardService.grantShopItemReward(user.getId(), freeze.getCode(), 1, 10,
				CoinSourceType.MISSION_REWARD, "mission:2"));

		assertThat(first.grantedQuantity()).isEqualTo(1);
		assertThat(second.grantedQuantity()).isZero();
		assertThat(second.fallbackCoin()).isEqualTo(10);
		assertThat(quantity(user, freeze)).isEqualTo(2);
		assertThat(frozenDays(user)).containsExactly(T.minusDays(1));
	}

	@Test
	void aUnitIsNeitherActivatedNorEquipped() { // AC-SF-06
		User user = learner(T, 0);
		ShopItem freeze = freeze(2);
		hold(user, freeze, 1);

		assertShopError(() -> inventoryService.activate(user.getId(), freeze.getId(), "act"),
				ShopErrorCode.ITEM_NOT_ACTIVATABLE);
		assertShopError(() -> inventoryService.equip(user.getId(), "THEME", freeze.getId()),
				ShopErrorCode.ITEM_NOT_EQUIPPABLE);
		assertShopError(() -> inventoryService.equip(user.getId(), "STREAK_FREEZE", freeze.getId()),
				ShopErrorCode.INVALID_EQUIPMENT_SLOT);
	}

	@Test
	void studyingAndBuyingAtOnceSettleExactlyOnce() throws Exception { // AC-SF-04
		User user = learner(T.minusDays(2), 1000);
		ShopItem freeze = freeze(2);
		hold(user, freeze, 1);

		List<Object> outcomes = runConcurrently(
				() -> {
					study(user, "c");
					return "studied";
				},
				() -> shopService.purchase(user.getId(), freeze.getId(), 200L, "race"));

		assertThat(outcomes).noneMatch(Exception.class::isInstance);
		assertThat(frozenDays(user)).containsExactly(T.minusDays(1));
		assertThat(quantity(user, freeze)).isEqualTo(1);
		assertThat(streakDays(user)).isEqualTo(S + 1);
	}

	// --- helpers --------------------------------------------------------------------------------

	private void study(User user, String key) {
		progressService.record(user.getId(), LearningEventType.QUIZ_COMPLETED, "freeze-it:" + user.getId() + ":" + key,
				Map.of());
	}

	/** Chain of S days whose last study day is {@code lastStudied}. */
	private User learner(LocalDate lastStudied, long coin) {
		Instant noon = lastStudied.atTime(12, 0).atZone(ProgressService.ZONE).toInstant();
		return userRepository.save(User.builder()
				.email("freeze-it-" + UUID.randomUUID() + "@test.local")
				.password("x".repeat(60))
				.firstName("IT")
				.activated(true)
				.coin(coin)
				.streakDays(S)
				.longestStreakDays(S)
				.lastStudiedAt(noon)
				.build());
	}

	private ShopItem freeze(int max) {
		return shopItemRepository.save(ShopItem.builder()
				.code("IT_FREEZE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase())
				.type(ShopItemType.STREAK_FREEZE)
				.name("Đóng băng chuỗi")
				.status(ShopItemStatus.PUBLISHED)
				.purchasable(true)
				.price(200L)
				.maxQuantity(max)
				.build());
	}

	private void hold(User user, ShopItem item, int quantity) {
		userItemRepository.save(UserItem.builder().user(user).item(item).itemType(item.getType()).quantity(quantity)
				.build());
	}

	private List<LocalDate> frozenDays(User user) {
		return jdbc().queryForList("SELECT frozen_date FROM streak_freeze_days WHERE user_id = ? ORDER BY frozen_date",
				LocalDate.class, user.getId());
	}

	private int quantity(User user, ShopItem item) {
		return jdbc().queryForObject("SELECT quantity FROM user_items WHERE user_id = ? AND shop_item_id = ?",
				Integer.class, user.getId(), item.getId());
	}

	private int streakDays(User user) {
		return jdbc().queryForObject("SELECT streak_days FROM users WHERE id = ?", Integer.class, user.getId());
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private static void assertShopError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ShopErrorCode code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ShopException.class,
				e -> assertThat(e.getCode()).isEqualTo(code));
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
					} catch (RuntimeException e) {
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
}
