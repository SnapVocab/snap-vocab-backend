package vn.ptit.snapvocab.badge.domain.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/**
 * {@code GET /api/me/badges} and {@code POST /api/me/badges/sync} (badges.md §8.1, §8.2). {@code coin}/{@code exp} are
 * the balances after the call, so the app can refresh its header after a sync that paid rewards (PD-BDG-14).
 */
public record BadgesDTO(int earnedCount, int totalCount, int unseenCount, RewardTotals rewardTotals, long coin,
		long exp, List<Item> badges) {

	/** {@code earnedAt} and {@code seen} are null while the badge is not earned; {@code imageUrl} null without art. */
	public record Item(Long id, String code, String name, String description, BadgeMetric metric, int threshold,
			long currentValue, String imageUrl, int sortOrder, boolean earned, Instant earnedAt, Boolean seen,
			Reward reward) {
	}

	/**
	 * The catalog reward while not earned, the snapshot once earned. Null when there is none, including badges earned
	 * before rewards existed (no backfill).
	 */
	public record Reward(int coin, int xp, RewardItem item) {
	}

	/** {@code granted}: null until earned, false when the learner already held the item. */
	public record RewardItem(String code, String name, ShopItemType type, String previewUrl, Boolean granted) {
	}

	/** Coin and XP from the earned badges' snapshots ("Thưởng đã tích lũy"). */
	public record RewardTotals(long coin, long xp) {
	}
}
