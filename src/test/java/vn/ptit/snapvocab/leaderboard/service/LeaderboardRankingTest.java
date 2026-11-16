package vn.ptit.snapvocab.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot.Entry;

/** U-02: the §3.3 example table (LB-04, LB-07). */
class LeaderboardRankingTest {

	private static Entry entry(long userId, long score, String reachedAtVn) {
		return new Entry(userId, score, Instant.parse(reachedAtVn).minusSeconds(7 * 3600), 0, false, 0, score, 0, 1, 1,
				score);
	}

	@Test
	void sharedRanksTieOrderAndGaps() {
		List<Entry> ranked = LeaderboardRanking.rank(List.of(
				entry(5, 40, "2026-10-19T20:00:00Z"),
				entry(12, 90, "2026-10-20T11:00:00Z"),
				entry(7, 120, "2026-10-20T10:00:00Z"),
				entry(9, 90, "2026-10-20T11:00:00Z"),
				entry(3, 90, "2026-10-20T09:00:00Z")));

		assertThat(ranked).extracting(Entry::userId).containsExactly(7L, 3L, 9L, 12L, 5L);
		assertThat(ranked).extracting(Entry::rank).containsExactly(1, 2, 2, 2, 5);
		assertThat(ranked).extracting(Entry::tied).containsExactly(false, true, true, true, false);
		assertThat(LeaderboardRanking.xpToNextRank(ranked, 0)).isNull();
		assertThat(LeaderboardRanking.nextRank(ranked, 0)).isNull();
		for (int i = 1; i <= 3; i++) {
			assertThat(LeaderboardRanking.xpToNextRank(ranked, i)).isEqualTo(31L);
			assertThat(LeaderboardRanking.nextRank(ranked, i)).isEqualTo(1);
		}
		assertThat(LeaderboardRanking.xpToNextRank(ranked, 4)).isEqualTo(51L);
		assertThat(LeaderboardRanking.nextRank(ranked, 4)).isEqualTo(2);
		assertThat(LeaderboardRanking.indexOf(ranked, 12)).isEqualTo(3);
		assertThat(LeaderboardRanking.indexOf(ranked, 99)).isEqualTo(-1);
	}

	@Test
	void tiedFirstPlaceHasNoGapAndNonPositiveScoresDrop() {
		List<Entry> ranked = LeaderboardRanking.rank(List.of(entry(1, 50, "2026-10-20T10:00:00Z"),
				entry(2, 50, "2026-10-20T11:00:00Z"), entry(3, 0, "2026-10-20T12:00:00Z")));
		assertThat(ranked).extracting(Entry::rank).containsExactly(1, 1);
		assertThat(LeaderboardRanking.xpToNextRank(ranked, 1)).isNull();
		assertThat(LeaderboardRanking.rank(List.of())).isEmpty();
	}
}
