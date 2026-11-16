package vn.ptit.snapvocab.leaderboard.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot;

/** LB-04 and LB-07 as pure functions over snapshot entries. */
final class LeaderboardRanking {

	static final Comparator<LeaderboardSnapshot.Entry> ORDER = Comparator
			.comparingLong(LeaderboardSnapshot.Entry::score).reversed()
			.thenComparing(LeaderboardSnapshot.Entry::reachedAt)
			.thenComparingLong(LeaderboardSnapshot.Entry::userId);

	private LeaderboardRanking() {
	}

	/** Sorts and assigns shared ranks: scores 120, 90, 90, 40 → ranks 1, 2, 2, 4. Drops scores ≤ 0. */
	static List<LeaderboardSnapshot.Entry> rank(List<LeaderboardSnapshot.Entry> unranked) {
		List<LeaderboardSnapshot.Entry> sorted = unranked.stream().filter(e -> e.score() > 0).sorted(ORDER).toList();
		List<LeaderboardSnapshot.Entry> ranked = new ArrayList<>(sorted.size());
		int rank = 0;
		for (int i = 0; i < sorted.size(); i++) {
			long score = sorted.get(i).score();
			if (i == 0 || score != sorted.get(i - 1).score()) {
				rank = i + 1;
			}
			boolean tied = (i > 0 && sorted.get(i - 1).score() == score)
					|| (i + 1 < sorted.size() && sorted.get(i + 1).score() == score);
			ranked.add(sorted.get(i).ranked(rank, tied));
		}
		return ranked;
	}

	static int indexOf(List<LeaderboardSnapshot.Entry> entries, long userId) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).userId() == userId) {
				return i;
			}
		}
		return -1;
	}

	/** XP to pass the nearest higher score group; entries [0, rank − 2] all score higher. Null at rank 1. */
	static Long xpToNextRank(List<LeaderboardSnapshot.Entry> entries, int index) {
		LeaderboardSnapshot.Entry me = entries.get(index);
		return me.rank() == 1 ? null : entries.get(me.rank() - 2).score() - me.score() + 1;
	}

	static Integer nextRank(List<LeaderboardSnapshot.Entry> entries, int index) {
		LeaderboardSnapshot.Entry me = entries.get(index);
		return me.rank() == 1 ? null : entries.get(me.rank() - 2).rank();
	}
}
