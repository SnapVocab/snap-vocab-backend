package vn.ptit.snapvocab.leaderboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One ranking of one window, immutable once stored (leaderboard_backend.md §5). Entries are in LB-04 order. */
public record LeaderboardSnapshot(LocalDate weekStart, Instant windowEnd, boolean live, Instant computedAt,
		boolean complete, long eligibleLearners, List<Entry> entries) {

	public record Entry(long userId, long score, Instant reachedAt, int rank, boolean tied, long reviewXp,
			long quizXp, long bonusXp, int events, int activeDays, long peakHourXp) {

		public Entry ranked(int rank, boolean tied) {
			return new Entry(userId, score, reachedAt, rank, tied, reviewXp, quizXp, bonusXp, events, activeDays,
					peakHourXp);
		}
	}
}
