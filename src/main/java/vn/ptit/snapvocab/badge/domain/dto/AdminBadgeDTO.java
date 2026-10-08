package vn.ptit.snapvocab.badge.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;

/** Read-only Admin catalog (badges.md §8.4, P1). Version stays null until the P2 schema migration. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminBadgeDTO(Long id, String code, String name, String description, BadgeMetric metric,
		Integer threshold, String iconKey, Integer sortOrder, @JsonProperty("isActive") Boolean isActive,
		Long version, Long earnerCount, Instant createdAt, Instant updatedAt) {

	public static AdminBadgeDTO of(Badge badge, long earnerCount) {
		return new AdminBadgeDTO(badge.getId(), badge.getCode(), badge.getName(), badge.getDescription(),
				badge.getMetric(), badge.getThreshold(), badge.getIconKey(), badge.getSortOrder(), badge.getActive(),
				null, earnerCount, badge.getCreatedAt(), badge.getUpdatedAt());
	}
}
