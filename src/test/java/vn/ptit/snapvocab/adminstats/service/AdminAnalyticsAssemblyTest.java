package vn.ptit.snapvocab.adminstats.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Cards;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Cohort;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CohortActive;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CohortSize;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.StateCount;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.StreakRow;

/** admin_analytics_backend.md §6 assembly, without a database (§10 last line). */
class AdminAnalyticsAssemblyTest {

	@Test
	void cardsSplitReviewAtMasteredAndAddNeverReviewedToNew() { // A-02 shape
		List<StateCount> rows = List.of(state(0, 4, 0, 1), state(1, 1, 0, 1), state(2, 3, 1, 0), state(4, 1, 0, 0));
		assertThat(AdminAnalyticsService.cards(rows, 2)).isEqualTo(new Cards(6, 1, 0, 2, 1, 1, 2));
		assertThat(AdminAnalyticsService.cards(List.of(), 0)).isEqualTo(new Cards(0, 0, 0, 0, 0, 0, 0));
	}

	@Test
	void cohortsShrinkByOneWeekEachAndDropOutOfRangeRows() { // A-07
		LocalDate today = LocalDate.parse("2026-10-21"); // Wednesday
		LocalDate first = AdminAnalyticsService.firstMonday(today);
		assertThat(first).isEqualTo(LocalDate.parse("2026-08-31"));

		List<Cohort> cohorts = AdminAnalyticsService.cohorts(first,
				List.of(size(4, 1), size(9, 5)),
				List.of(active(4, 5, 1), active(4, 7, 1), active(4, 3, 9), active(-1, 0, 9)));
		assertThat(cohorts).extracting(Cohort::weekStart).startsWith("2026-08-31").endsWith("2026-10-19");
		assertThat(cohorts).extracting(c -> c.active().size()).containsExactly(8, 7, 6, 5, 4, 3, 2, 1);
		assertThat(cohorts.get(4)).isEqualTo(new Cohort("2026-09-28", 1, List.of(0L, 1L, 0L, 1L)));
	}

	@Test
	void longestReachedCountsEveryThresholdAtOrBelow() { // A-09
		List<Long> reached = AdminAnalyticsService.longestReached(
				List.of(streak(1, 1), streak(3, 2), streak(30, 1)));
		assertThat(reached).hasSize(30).startsWith(4L, 3L, 3L, 1L).endsWith(1L);
	}

	private static StateCount state(int state, long total, long mastered, long due) {
		return new StateCount() {
			public Integer getState() { return state; }
			public Long getTotal() { return total; }
			public Long getMastered() { return mastered; }
			public Long getDue() { return due; }
		};
	}

	private static CohortSize size(int cohort, long count) {
		return new CohortSize() {
			public Integer getCohort() { return cohort; }
			public Long getCount() { return count; }
		};
	}

	private static CohortActive active(int cohort, int week, long learners) {
		return new CohortActive() {
			public Integer getCohort() { return cohort; }
			public Integer getWeek() { return week; }
			public Long getLearners() { return learners; }
		};
	}

	private static StreakRow streak(int days, long count) {
		return new StreakRow() {
			public Integer getDays() { return days; }
			public Long getCount() { return count; }
		};
	}
}
