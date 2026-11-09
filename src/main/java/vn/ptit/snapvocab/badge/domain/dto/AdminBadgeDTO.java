package vn.ptit.snapvocab.badge.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;

/**
 * Admin catalog (badges.md §8.4). {@code version} is the optimistic lock every PATCH must send back (P2).
 * {@code rewardItem} describes {@code rewardItemCode} so the list needs no Shop lookup per row (PD-BDG-14).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminBadgeDTO(Long id, String code, String name, String description, BadgeMetric metric,
		Integer threshold, String imageKey, String imageUrl, Integer sortOrder,
		@JsonProperty("isActive") Boolean isActive, Integer rewardCoin, Integer rewardXp, String rewardItemCode,
		RewardItem rewardItem, Long version, Long earnerCount, Instant createdAt, Instant updatedAt) {

	public record RewardItem(String name, ShopItemType type, ShopItemStatus status, Boolean purchasable,
			String previewUrl) {
	}

	public static AdminBadgeDTO of(Badge badge, String imageUrl, long earnerCount, RewardItem rewardItem) {
		return new AdminBadgeDTO(badge.getId(), badge.getCode(), badge.getName(), badge.getDescription(),
				badge.getMetric(), badge.getThreshold(), badge.getImageKey(), imageUrl, badge.getSortOrder(),
				badge.getActive(),
				badge.getRewardCoin(), badge.getRewardXp(), badge.getRewardItemCode(), rewardItem,
				badge.getVersion(), earnerCount, badge.getCreatedAt(), badge.getUpdatedAt());
	}
}
