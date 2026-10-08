package vn.ptit.snapvocab.badge.domain.dto;

import java.util.List;

/** {@code POST /api/me/badges/seen} body (badges.md §8.3); validated in {@code BadgeService.markSeen}. */
public record SeenBadgesRequest(List<Long> badgeIds) {
}
