package vn.ptit.snapvocab.leaderboard.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.ptit.snapvocab.xp.domain.XpSourceType;

/** GET /api/admin/leaderboards (leaderboard_backend.md §7.2): the whole board of one snapshot with raw signals. */
public record AdminLeaderboardDTO(LocalDate weekStart, LocalDate weekEnd, String timezone, Instant endsAt,
		@JsonProperty("isFinal") boolean isFinal, Instant asOf, List<XpSourceType> countedSources,
		long eligibleLearners, int participants, long totalXp, List<Row> entries) {

	public record Row(int rank, boolean tied, long userId, String displayName, String email, long score,
			Instant reachedAt, long reviewXp, long quizXp, long bonusXp, int events, int activeDays,
			long peakHourXp, Integer rankChange) {
	}
}
