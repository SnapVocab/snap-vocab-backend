package vn.ptit.snapvocab.adminstats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.CoinDay;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ErrorCount;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanConfig;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanDay;

/** GET /api/admin/analytics; must match snapAdmin/src/domains/admin-analytics/types.ts field for field. */
public record AdminAnalyticsDTO(Instant generatedAt, int days, Learning learning, Retention retention, Economy economy,
		Scan scan) {

	public record Learning(Cards cards, List<LearningDay> daily, List<TopicStat> topics, List<HardWord> hardWords) {}

	/** "new" is a Java keyword: the component is renamed, the JSON key is not. */
	public record Cards(@JsonProperty("new") long newCount, long learning, long relearning, long review, long mastered,
			long suspended, long dueToday) {}

	public record LearningDay(String date, long reviews, long newWords, long quizAnswers, long quizCorrect) {}

	public record TopicStat(long collectionId, long rootId, String rootName, long topicId, String name, long answers,
			long correct, long learners) {}

	public record HardWord(String word, long collectionId, long rootId, long topicId, String topicName, long answers,
			long wrong, long lapses, long learners) {}

	public record Retention(long activated, List<Cohort> cohorts, List<Long> longestReached, List<Segment> segments) {}

	public record Cohort(String weekStart, long size, List<Long> active) {}

	public record Segment(SegmentId id, long count) {}

	public enum SegmentId { STREAK_AT_RISK, IDLE_8_30, NEVER_STUDIED, PENDING_EMAIL }

	public record Economy(List<CoinDay> daily, List<MissionCoin> inByMission, long adjustments, List<ItemSales> sales,
			long circulation) {}

	public record MissionCoin(String code, long coin) {}

	public record ItemSales(String code, long purchases, long coin, long prevPurchases) {}

	public record Scan(ScanConfig config, String modelVersion, List<ScanDay> daily, Funnel funnel,
			Reliability reliability, Latency latency, List<ErrorCount> errors, long quotaHitLearners,
			List<UnmappedLabel> unmappedLabels) {}

	/** saved is null until a saved word carries its source scan (KT-A7). */
	public record Funnel(long submitted, long done, long withLabels, long withWord, Long saved) {}

	public record Reliability(@JsonProperty("HIGH") long high, @JsonProperty("MEDIUM") long medium,
			@JsonProperty("LOW") long low) {}

	public record Latency(Long p50Ms, Long p95Ms, Long p99Ms) {}

	public record UnmappedLabel(String label, long scans, long learners, String lastSeen) {}
}
