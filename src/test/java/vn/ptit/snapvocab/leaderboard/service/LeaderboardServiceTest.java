package vn.ptit.snapvocab.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot.Entry;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWeek;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWindow;
import vn.ptit.snapvocab.leaderboard.repository.LeaderboardRepository;
import vn.ptit.snapvocab.user.service.AvatarUrlService;

/** U-03 displayName, U-04 rankChange, U-05 snapshot JSON round trip. */
class LeaderboardServiceTest {

	private static Entry ranked(long userId, int rank) {
		return new Entry(userId, 100 - rank, Instant.EPOCH, rank, false, 0, 0, 0, 1, 1, 0);
	}

	@Test
	void displayName() {
		assertThat(LeaderboardService.displayName("Trọng", "Hoàng")).isEqualTo("Trọng Hoàng");
		assertThat(LeaderboardService.displayName("Trọng", null)).isEqualTo("Trọng");
		assertThat(LeaderboardService.displayName(" ", "")).isEqualTo("Người học");
	}

	@Test
	void rankChangeAgainstMidnight() {
		Map<Long, Integer> baseline = Map.of(3L, 1, 7L, 2, 5L, 3);
		assertThat(LeaderboardService.rankChange(baseline, ranked(3, 2))).isEqualTo(-1);
		assertThat(LeaderboardService.rankChange(baseline, ranked(7, 1))).isEqualTo(1);
		assertThat(LeaderboardService.rankChange(baseline, ranked(5, 5))).isEqualTo(-2);
		assertThat(LeaderboardService.rankChange(baseline, ranked(9, 2))).isNull();
	}

	@Test
	void previousWeekNeverReadsTheBaseline() {
		Instant wednesday = Instant.parse("2026-10-21T05:00:00Z");
		LeaderboardSnapshotService snapshots = mock(LeaderboardSnapshotService.class);
		when(snapshots.get(any())).thenReturn(new LeaderboardSnapshot(LocalDate.parse("2026-10-12"), wednesday, false,
				wednesday, true, 1, List.of(ranked(1, 1))));
		LeaderboardService service = new LeaderboardService(snapshots, mock(LeaderboardRepository.class),
				mock(AvatarUrlService.class), Clock.fixed(wednesday, ZoneOffset.UTC));

		assertThat(service.forLearner(1, true, LeaderboardWeek.PREVIOUS, 50).me().rankChange()).isNull();
		verify(snapshots, never()).get(argThat(LeaderboardWindow::live));
		verify(snapshots, never()).get(argThat(w -> w.cutoff() != null && w.cutoff().equals(LocalDate.parse("2026-10-21"))));
	}

	@Test
	void snapshotSurvivesTheJsonRoundTripWithMicroseconds() throws Exception {
		ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
		LeaderboardSnapshot snapshot = new LeaderboardSnapshot(LocalDate.parse("2026-10-19"),
				Instant.parse("2026-10-25T17:00:00Z"), true, Instant.parse("2026-10-21T05:00:12.418Z"), false, 140,
				List.of(new Entry(12, 1240, Instant.parse("2026-10-21T03:41:09.552107Z"), 1, false, 240, 1000, 300, 160,
						3, 520)));
		assertThat(mapper.readValue(mapper.writeValueAsString(snapshot), LeaderboardSnapshot.class)).isEqualTo(snapshot);
	}
}
