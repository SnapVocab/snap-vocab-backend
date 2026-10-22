package vn.ptit.snapvocab.shop.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.ptit.snapvocab.shop.domain.enumeration.ThemeKey;

/**
 * Type-specific runtime config (shop.md §10.1): THEME {themeKey}, AVATAR_FRAME {frameUrl},
 * XP_BOOSTER {multiplier, durationMinutes, maxQuantity}, STREAK_FREEZE {maxQuantity}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemConfig(ThemeKey themeKey, String frameUrl, Integer multiplier, Integer durationMinutes,
		Integer maxQuantity) {
}
