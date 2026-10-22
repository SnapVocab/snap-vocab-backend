package vn.ptit.snapvocab.progress.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.progress.service.StreakFreezeService.Settlement;

/** AC-SF-01/02: the pure rule of streak_freeze.md §3.2 on the examples of §4 (T = today, S = 5). */
class StreakFreezeServiceTest {

	private static final LocalDate T = LocalDate.of(2026, 10, 21);
	private static final int S = 5;

	private static Settlement settle(LocalDate lastStudied, LocalDate lastFrozen, int held, LocalDate today) {
		return StreakFreezeService.settle(S, lastStudied, lastFrozen, held, today);
	}

	@Test
	void studiedYesterdayUsesNothing() { // §4 #1
		Settlement s = settle(T.minusDays(1), null, 1, T);
		assertThat(s).isEqualTo(new Settlement(0, false, T.minusDays(1), 1, null));
		assertThat(s.streak(S, T)).isEqualTo(S);
	}

	@Test
	void oneMissedDayTakesOneUnitAndKeepsTheCount() { // §4 #2
		Settlement s = settle(T.minusDays(2), null, 1, T);
		assertThat(s).isEqualTo(new Settlement(1, false, T.minusDays(1), 0, T.minusDays(1)));
		assertThat(s.streak(S, T)).isEqualTo(S);
		// Studying today then continues the chain from the covered day.
		assertThat(ProgressService.nextStreak(S, s.coveredThrough(), T)).isEqualTo(S + 1);
	}

	@Test
	void runningOutMidGapUsesTheUnitAndBreaks() { // §4 #3
		Settlement s = settle(T.minusDays(3), null, 1, T);
		assertThat(s).isEqualTo(new Settlement(1, true, T.minusDays(2), 0, T.minusDays(2)));
		assertThat(s.streak(S, T)).isZero();
	}

	@Test
	void enoughUnitsCoverTheWholeGap() { // §4 #4
		Settlement s = settle(T.minusDays(3), null, 2, T);
		assertThat(s).isEqualTo(new Settlement(2, false, T.minusDays(1), 0, T.minusDays(1)));
		assertThat(s.streak(S, T)).isEqualTo(S);
	}

	@Test
	void aClosedOrMissingChainNeverTakesUnits() { // §4 #5, #7
		assertThat(StreakFreezeService.settle(0, T.minusDays(2), null, 1, T).consumed()).isZero();
		assertThat(StreakFreezeService.settle(0, null, null, 2, T).consumed()).isZero();
	}

	@Test
	void todayIsNeverCovered() { // §4 #6: a unit bought today covers today only once today is over
		assertThat(settle(T.minusDays(1), null, 1, T).consumed()).isZero();
		assertThat(settle(T.minusDays(1), null, 1, T.plusDays(1)))
				.isEqualTo(new Settlement(1, false, T, 0, T));
	}

	@Test
	void anEarlierFrozenDayIsNotCountedTwice() {
		// Studied T-3, T-2 already frozen: only T-1 is still missing.
		assertThat(settle(T.minusDays(3), T.minusDays(2), 1, T))
				.isEqualTo(new Settlement(1, false, T.minusDays(1), 0, T.minusDays(1)));
		// Studied after the last frozen day: the study day is what the chain reaches.
		assertThat(settle(T.minusDays(1), T.minusDays(5), 1, T).consumed()).isZero();
	}

	@Test
	void outcomeDoesNotDependOnWhenTheAppWasOpened() { // §3.3, §4 #10
		Settlement once = settle(T.minusDays(3), null, 1, T);
		Settlement first = settle(T.minusDays(3), null, 1, T.minusDays(1));
		Settlement then = settle(T.minusDays(3), first.lastFrozen(), first.remaining(), T);
		assertThat(first.consumed() + then.consumed()).isEqualTo(once.consumed());
		assertThat(then.broken()).isEqualTo(once.broken()).isTrue();
		assertThat(then.lastFrozen()).isEqualTo(once.lastFrozen());
	}
}
