package vn.ptit.snapvocab.shop.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Type-specific runtime config (shop.md §10.1): AVATAR_FRAME and CARD_FRAME {frameUrl}, XP_BOOSTER {multiplier, durationMinutes, maxQuantity}, STREAK_FREEZE {maxQuantity}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemConfig(String frameUrl, Integer multiplier, Integer durationMinutes,
		Integer maxQuantity) {
}
