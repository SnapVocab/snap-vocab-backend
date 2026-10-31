package vn.ptit.snapvocab.progress.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.progress.domain.dto.ProgressHistoryDTO.Bucket;
import vn.ptit.snapvocab.progress.domain.enumeration.HistoryPeriod;

class ProgressServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 2); // Friday

	@Test
	void streakAdvancesOnlyOnConsecutiveDays() {
		assertThat(ProgressService.nextStreak(0, null, TODAY)).isEqualTo(1);
		assertThat(ProgressService.nextStreak(4, TODAY, TODAY)).isEqualTo(4);
		assertThat(ProgressService.nextStreak(4, TODAY.minusDays(1), TODAY)).isEqualTo(5);
		assertThat(ProgressService.nextStreak(4, TODAY.minusDays(2), TODAY)).isEqualTo(1);
		assertThat(ProgressService.nextStreak(1, TODAY, TODAY)).isEqualTo(1);
		assertThat(ProgressService.nextStreak(0, TODAY.minusDays(1), TODAY)).isEqualTo(2);
	}

	@Test
	void missedDaysAndNewLearnersHaveABaselineOfOne() {
		assertThat(ProgressService.currentStreak(4, TODAY, TODAY)).isEqualTo(4);
		assertThat(ProgressService.currentStreak(4, TODAY.minusDays(1), TODAY)).isEqualTo(4);
		assertThat(ProgressService.currentStreak(4, TODAY.minusDays(2), TODAY)).isEqualTo(1);
		assertThat(ProgressService.currentStreak(0, null, TODAY)).isEqualTo(1);
		assertThat(ProgressService.currentStreak(0, TODAY, TODAY)).isEqualTo(1);
		assertThat(ProgressService.currentStreak(4, TODAY.plusDays(1), TODAY)).isEqualTo(1);
	}

	@Test
	void weekBucketsStartMondayAndOnlyCountDaysInRange() {
		Map<LocalDate, Long> perDay = Map.of(
				LocalDate.of(2026, 9, 21), 9L,   // Monday before `from` — excluded
				LocalDate.of(2026, 9, 24), 2L,
				LocalDate.of(2026, 9, 25), 1L,
				LocalDate.of(2026, 9, 29), 3L);
		List<Bucket> buckets = ProgressService.buckets(HistoryPeriod.WEEK, LocalDate.of(2026, 9, 24), TODAY, perDay);
		assertThat(buckets).containsExactly(
				new Bucket(LocalDate.of(2026, 9, 21), 3, 2),
				new Bucket(LocalDate.of(2026, 9, 28), 3, 1));
	}

	@Test
	void dayBucketsIncludeEmptyDays() {
		List<Bucket> buckets = ProgressService.buckets(HistoryPeriod.DAY, TODAY.minusDays(2), TODAY,
				Map.of(TODAY, 5L));
		assertThat(buckets).extracting(Bucket::eventCount).containsExactly(0L, 0L, 5L);
	}

	@Test
	void monthBucketsAlignToFirstDay() {
		List<Bucket> buckets = ProgressService.buckets(HistoryPeriod.MONTH, LocalDate.of(2026, 9, 15), TODAY,
				Map.of(LocalDate.of(2026, 9, 1), 7L, LocalDate.of(2026, 9, 20), 1L));
		assertThat(buckets).containsExactly(
				new Bucket(LocalDate.of(2026, 9, 1), 1, 1),
				new Bucket(LocalDate.of(2026, 10, 1), 0, 0));
	}
}
