package vn.ptit.snapvocab.domain.dto.scan;

import java.time.Instant;

/** @param resetAt next midnight in the configured zone, when {@code used} drops back to 0 */
public record ScanQuotaDTO(int limit, int used, int remaining, Instant resetAt) {
}
