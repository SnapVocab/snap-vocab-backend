package vn.ptit.snapvocab.badge.domain.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;

/** {@code GET /api/me/badges} and {@code POST /api/me/badges/sync} (badges.md §8.1, §8.2). */
public record BadgesDTO(int earnedCount, int totalCount, int unseenCount, List<Item> badges) {

	/** {@code earnedAt} and {@code seen} are null while the badge is not earned. */
	public record Item(Long id, String code, String name, String description, BadgeMetric metric, int threshold,
			long currentValue, String iconKey, int sortOrder, boolean earned, Instant earnedAt, Boolean seen) {
	}
}
