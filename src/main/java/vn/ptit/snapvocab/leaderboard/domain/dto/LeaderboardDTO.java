package vn.ptit.snapvocab.leaderboard.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** GET /api/leaderboards (leaderboard_backend.md §6.2). No userId, email or admin signal (LB-10). */
public record LeaderboardDTO(LocalDate weekStart, LocalDate weekEnd, String timezone, Instant endsAt,
		@JsonProperty("isFinal") boolean isFinal, Instant asOf, int participants, List<Row> entries, Me me) {

	public record Row(int rank, String displayName, String avatarUrl, String avatarFrameUrl, long score,
			Integer rankChange, @JsonProperty("isMe") boolean isMe) {
	}

	public record Me(boolean eligible, Integer rank, long score, Integer rankChange, Long xpToNextRank,
			Integer nextRank) {
	}
}
