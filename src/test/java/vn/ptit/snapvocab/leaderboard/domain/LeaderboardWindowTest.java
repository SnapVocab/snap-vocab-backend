package vn.ptit.snapvocab.leaderboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** U-01 (leaderboard_backend.md §3.2, §5.1). */
class LeaderboardWindowTest {

	@Test
	void weekBoundariesFollowVietnamTime() {
		assertThat(LeaderboardWindow.weekStartOf(Instant.parse("2026-10-18T16:59:59.999999Z")))
				.isEqualTo(LocalDate.parse("2026-10-12"));
		assertThat(LeaderboardWindow.weekStartOf(Instant.parse("2026-10-18T17:00:00Z")))
				.isEqualTo(LocalDate.parse("2026-10-19"));
		assertThat(LeaderboardWindow.weekStartOf(Instant.parse("2026-10-25T16:59:59.999999Z")))
				.isEqualTo(LocalDate.parse("2026-10-19"));
		assertThat(LeaderboardWindow.weekStartOf(Instant.parse("2026-10-25T17:00:00Z")))
				.isEqualTo(LocalDate.parse("2026-10-26"));

		LeaderboardWindow live = LeaderboardWindow.live(Instant.parse("2026-10-21T05:00:00Z"));
		assertThat(live.start()).isEqualTo(Instant.parse("2026-10-18T17:00:00Z"));
		assertThat(live.end()).isEqualTo(Instant.parse("2026-10-25T17:00:00Z"));
		assertThat(live.weekEnd()).isEqualTo(LocalDate.parse("2026-10-25"));
		assertThat(live.endsAt()).isEqualTo(live.end());
	}

	@Test
	void previousWeekEndsWhereTheCurrentOneStarts() {
		Instant wednesday = Instant.parse("2026-10-21T05:00:00Z");
		LeaderboardWindow previous = LeaderboardWindow.previousWeek(wednesday);
		assertThat(previous.weekStart()).isEqualTo(LocalDate.parse("2026-10-12"));
		assertThat(previous.end()).isEqualTo(LeaderboardWindow.live(wednesday).start());
		assertThat(previous.endsAt()).isEqualTo(previous.end());
	}

	@Test
	void baselineIsMidnightTodayAndAbsentOnMonday() {
		assertThat(LeaderboardWindow.baseline(Instant.parse("2026-10-19T03:00:00Z"))).isEmpty(); // Monday 10:00
		LeaderboardWindow baseline = LeaderboardWindow.baseline(Instant.parse("2026-10-21T05:00:00Z")).orElseThrow();
		assertThat(baseline.start()).isEqualTo(Instant.parse("2026-10-18T17:00:00Z"));
		assertThat(baseline.end()).isEqualTo(Instant.parse("2026-10-20T17:00:00Z")); // Wednesday 00:00
	}

	@Test
	void keysTtlsAndCompleteness() {
		Instant wednesday = Instant.parse("2026-10-21T05:00:00Z");
		LeaderboardWindow live = LeaderboardWindow.live(wednesday);
		LeaderboardWindow baseline = LeaderboardWindow.baseline(wednesday).orElseThrow();
		LeaderboardWindow previous = LeaderboardWindow.previousWeek(wednesday);
		assertThat(live.key()).isEqualTo("leaderboard:v1:2026-10-19:live");
		assertThat(baseline.key()).isEqualTo("leaderboard:v1:2026-10-19:upto:2026-10-21");
		assertThat(previous.key()).isEqualTo("leaderboard:v1:2026-10-12:upto:2026-10-19");
		assertThat(live.ttl().toMinutes()).isEqualTo(15);
		assertThat(previous.ttl().toDays()).isEqualTo(8);

		assertThat(previous.completeAt(previous.end().plusSeconds(119))).isFalse();
		assertThat(previous.completeAt(previous.end().plusSeconds(120))).isTrue();
		assertThat(live.completeAt(live.end().plusSeconds(3600))).isFalse();
	}
}
