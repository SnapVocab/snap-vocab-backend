package vn.ptit.snapvocab.shop.domain.dto;

import vn.ptit.snapvocab.shop.domain.enumeration.ThemeKey;

/** Equipped cosmetics on GET /api/users/me (shop.md §10.1); a null field means that slot uses the default. */
public record EquippedDTO(ThemeKey themeKey, String avatarFrameUrl) {
}
