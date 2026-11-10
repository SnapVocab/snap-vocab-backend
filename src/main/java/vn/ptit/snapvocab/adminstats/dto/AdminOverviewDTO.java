package vn.ptit.snapvocab.adminstats.dto;

import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.CoinDay;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ErrorCount;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanConfig;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanDay;

/** GET /api/admin/overview; must match snapAdmin/src/domains/admin-overview/types.ts field for field. */
public record AdminOverviewDTO(Instant generatedAt, Learners learners, Scan scan, List<UnmappedLabel> unmappedLabels,
		Economy economy, List<MissionStat> missions, List<ItemSales> shopSales30d) {

	public record Learners(long activated, long active7d, long activeToday, List<DayActive> daily, Today today) {}

	public record DayActive(String date, long active) {}

	public record Today(long newWords, long reviews, long quizzes, long missionsCompleted, long fullSets) {}

	public record Scan(ScanConfig config, ScanToday today, List<ScanDay> daily, List<ErrorCount> errors7d) {}

	/** p50Ms/p95Ms are null when no scan finished today. */
	public record ScanToday(long total, long failed, Long p50Ms, Long p95Ms, long queueFull, long quotaHit) {}

	public record UnmappedLabel(String label, long scans7d, long learners) {}

	public record Economy(List<CoinDay> daily, long avgBalance) {}

	public record MissionStat(String code, long assigned7d, long completed7d) {}

	public record ItemSales(String code, long purchases) {}
}
