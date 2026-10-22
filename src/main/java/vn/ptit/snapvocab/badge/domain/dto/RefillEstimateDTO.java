package vn.ptit.snapvocab.badge.domain.dto;

import java.time.Instant;

/** PD-BDG-13d: activated accounts at or past the threshold without the badge yet, at {@code calculatedAt}. */
public record RefillEstimateDTO(long eligibleCount, Instant calculatedAt, Long version) {
}
