package vn.ptit.snapvocab.badge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.UserBadge;
import vn.ptit.snapvocab.badge.domain.UserBadgeWordCredit;
import vn.ptit.snapvocab.badge.domain.dto.BadgesDTO;
import vn.ptit.snapvocab.badge.domain.dto.SeenBadgesResultDTO;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeWordCreditRepository;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.shop.service.ShopMapper;
import vn.ptit.snapvocab.shop.service.ShopRewardService;
import vn.ptit.snapvocab.shop.service.WalletService;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.xp.domain.XpSourceType;
import vn.ptit.snapvocab.xp.service.XpService;

/**
 * Badges (badges.md §6, §8). Badges are granted only by {@link #sync}; producers never grant, they only record word
 * credits ({@link #recordWordCredit}). Never revoked, no notification. The reward (PD-BDG-14) is paid in the same
 * transaction as the grant and snapshotted on {@code user_badges}: a failure rolls the grant back for the next sync.
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

	static final int MAX_SEEN_IDS = 50;

	private final UserRepository userRepository;
	private final QuizRepository quizRepository;
	private final BadgeRepository badgeRepository;
	private final UserBadgeRepository userBadgeRepository;
	private final UserBadgeWordCreditRepository wordCreditRepository;
	private final WalletService walletService;
	private final XpService xpService;
	private final ShopRewardService shopRewardService;
	private final ShopItemRepository shopItemRepository;
	private final ShopMapper shopMapper;

	/** §8.1: read-only; a badge that is due but not synced yet shows {@code earned = false}. */
	@Transactional(readOnly = true)
	public BadgesDTO getBadges(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
		return view(user, metrics(user), userBadgeRepository.findByUserId(userId));
	}

	/**
	 * §6.1: grants every active badge whose threshold is reached and not yet earned, then returns the view. The users
	 * lock serializes it with quiz complete, add-item and a concurrent sync; UNIQUE(user_id, badge_id) is the last line.
	 * READ_COMMITTED so a quiz committed while we waited for the lock is counted.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public BadgesDTO sync(Long userId) {
		User user = Economy.lockUser(userRepository, userId);
		Map<BadgeMetric, Long> values = metrics(user);
		List<UserBadge> earned = new ArrayList<>(userBadgeRepository.findByUserId(userId));
		List<Long> earnedIds = earned.stream().map(ub -> ub.getBadge().getId()).toList();
		for (Badge badge : badgeRepository.findAllByOrderBySortOrderAscIdAsc()) {
			if (badge.getActive() && !earnedIds.contains(badge.getId())
					&& values.get(badge.getMetric()) >= badge.getThreshold()) {
				earned.add(userBadgeRepository.save(rewarded(user, badge)));
			}
		}
		return view(user, values, earned);
	}

	/**
	 * Pays the badge's current reward and returns the grant carrying its snapshot. One event key per user and badge,
	 * so the ledgers' UNIQUE event_key backs up UNIQUE(user_id, badge_id). Reward XP is never boosted (BADGE_REWARD).
	 * A reward item has no Coin fallback: badge frames are never sold, so nobody holds one before earning it.
	 */
	private UserBadge rewarded(User user, Badge badge) {
		String eventKey = "BADGE_REWARD:" + user.getId() + ":" + badge.getId();
		if (badge.getRewardCoin() > 0) {
			walletService.apply(user, badge.getRewardCoin(), CoinSourceType.BADGE_REWARD, badge.getCode(),
					badge.getName(), eventKey);
		}
		if (badge.getRewardXp() > 0) {
			xpService.award(user.getId(), XpSourceType.BADGE_REWARD, badge.getRewardXp(), eventKey);
		}
		Boolean itemGranted = badge.getRewardItemCode() == null ? null
				: shopRewardService.grantShopItemReward(user.getId(), badge.getRewardItemCode(), 1, 0,
						CoinSourceType.BADGE_REWARD, eventKey + ":ITEM").grantedQuantity() == 1;
		return UserBadge.builder()
				.user(user)
				.badge(badge)
				.rewardCoin(badge.getRewardCoin())
				.rewardXp(badge.getRewardXp())
				.rewardItemCode(badge.getRewardItemCode())
				.rewardItemGranted(itemGranted)
				.build();
	}

	/** §8.3: idempotent; ids that are not the learner's unseen earned badges are ignored. */
	@Transactional
	public SeenBadgesResultDTO markSeen(Long userId, List<Long> badgeIds) {
		if (badgeIds == null || badgeIds.isEmpty() || badgeIds.size() > MAX_SEEN_IDS
				|| badgeIds.stream().anyMatch(id -> id == null || id <= 0)) {
			throw new BadRequestAlertException("VALIDATION_ERROR");
		}
		userBadgeRepository.markSeen(userId, badgeIds, Economy.now());
		return new SeenBadgesResultDTO(userBadgeRepository.countByUserIdAndSeenAtIsNull(userId));
	}

	/**
	 * §6.2: called from add-item for a quiz-ready item in the learner's own USER collection, inside its transaction
	 * and after it locked {@code users}. Counts each normalized word once per account; grants nothing.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordWordCredit(Long userId, byte[] wordHash, Instant actionInstant) {
		if (!wordCreditRepository.existsByUserIdAndWordHash(userId, wordHash)) {
			wordCreditRepository.save(UserBadgeWordCredit.builder()
					.user(userRepository.getReferenceById(userId))
					.wordHash(wordHash)
					.createdAt(actionInstant)
					.build());
		}
	}

	private Map<BadgeMetric, Long> metrics(User user) {
		Map<BadgeMetric, Long> values = new EnumMap<>(BadgeMetric.class);
		values.put(BadgeMetric.LONGEST_STREAK_DAYS, (long) user.getLongestStreakDays());
		values.put(BadgeMetric.QUIZ_COMPLETED, quizRepository.countCompleted(user.getId()));
		values.put(BadgeMetric.PERFECT_QUIZ, quizRepository.countPerfect(user.getId()));
		values.put(BadgeMetric.DISTINCT_WORDS_ADDED, wordCreditRepository.countByUserId(user.getId()));
		return values;
	}

	/** Active badges plus inactive ones the learner earned (§5.4), by sort order. */
	private BadgesDTO view(User user, Map<BadgeMetric, Long> values, List<UserBadge> earned) {
		Map<Long, UserBadge> byBadgeId = earned.stream()
				.collect(Collectors.toMap(ub -> ub.getBadge().getId(), Function.identity()));
		List<Badge> shown = badgeRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.filter(b -> b.getActive() || byBadgeId.containsKey(b.getId()))
				.toList();
		// One lookup for every reward item on screen (at most one per badge).
		List<String> codes = Stream.concat(shown.stream().map(Badge::getRewardItemCode),
				earned.stream().map(UserBadge::getRewardItemCode)).filter(Objects::nonNull).distinct().toList();
		Map<String, ShopItem> rewardItems = codes.isEmpty() ? Map.of()
				: shopItemRepository.findByCodeIn(codes).stream()
						.collect(Collectors.toMap(ShopItem::getCode, Function.identity()));
		List<BadgesDTO.Item> items = shown.stream()
				.map(b -> item(b, values.get(b.getMetric()), byBadgeId.get(b.getId()), rewardItems))
				.toList();
		int earnedCount = (int) items.stream().filter(BadgesDTO.Item::earned).count();
		int unseenCount = (int) items.stream().filter(i -> Boolean.FALSE.equals(i.seen())).count();
		BadgesDTO.RewardTotals totals = new BadgesDTO.RewardTotals(
				earned.stream().mapToLong(ub -> ub.getRewardCoin() == null ? 0 : ub.getRewardCoin()).sum(),
				earned.stream().mapToLong(ub -> ub.getRewardXp() == null ? 0 : ub.getRewardXp()).sum());
		return new BadgesDTO(earnedCount, items.size(), unseenCount, totals, user.getCoin(), user.getExp(), items);
	}

	private BadgesDTO.Item item(Badge b, long currentValue, UserBadge earned, Map<String, ShopItem> rewardItems) {
		return new BadgesDTO.Item(b.getId(), b.getCode(), b.getName(), b.getDescription(), b.getMetric(),
				b.getThreshold(), currentValue, b.getIconKey(), b.getSortOrder(), earned != null,
				earned == null ? null : earned.getCreatedAt(), earned == null ? null : earned.getSeenAt() != null,
				earned == null ? reward(b.getRewardCoin(), b.getRewardXp(), b.getRewardItemCode(), null, rewardItems)
						: earned.getRewardCoin() == null ? null
								: reward(earned.getRewardCoin(), earned.getRewardXp(), earned.getRewardItemCode(),
										earned.getRewardItemGranted(), rewardItems));
	}

	private BadgesDTO.Reward reward(int coin, int xp, String itemCode, Boolean granted,
			Map<String, ShopItem> rewardItems) {
		ShopItem item = itemCode == null ? null : rewardItems.get(itemCode);
		if (coin == 0 && xp == 0 && item == null) {
			return null;
		}
		return new BadgesDTO.Reward(coin, xp, item == null ? null
				: new BadgesDTO.RewardItem(item.getCode(), item.getName(), item.getType(),
						shopMapper.config(item).frameUrl(), granted));
	}
}
