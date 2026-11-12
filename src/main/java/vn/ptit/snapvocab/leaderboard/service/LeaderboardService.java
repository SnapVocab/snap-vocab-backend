package vn.ptit.snapvocab.leaderboard.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardSnapshot;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWeek;
import vn.ptit.snapvocab.leaderboard.domain.LeaderboardWindow;
import vn.ptit.snapvocab.leaderboard.domain.dto.AdminLeaderboardDTO;
import vn.ptit.snapvocab.leaderboard.domain.dto.LeaderboardDTO;
import vn.ptit.snapvocab.leaderboard.repository.LeaderboardRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.user.service.AvatarUrlService;

/** Builds the learner and admin responses from one snapshot each (leaderboard_backend.md §6.3, §7.3). */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

	static final int MAX_LIMIT = 50; // LB-05, OQ-LB-02
	private static final int PROFILE_BATCH = 1000;
	private static final String FALLBACK_NAME = "Người học";

	private final LeaderboardSnapshotService snapshots;
	private final LeaderboardRepository repository;
	private final AvatarUrlService avatarUrlService;
	private final Clock clock;

	public LeaderboardDTO forLearner(long userId, boolean eligible, LeaderboardWeek week, int limit) {
		Instant now = clock.instant();
		LeaderboardWindow window = window(week, now);
		LeaderboardSnapshot snapshot = snapshots.get(window);
		Map<Long, Integer> baseline = baselineRanks(week, now);
		List<LeaderboardSnapshot.Entry> entries = snapshot.entries();

		int n = Math.max(1, Math.min(limit, MAX_LIMIT));
		List<LeaderboardSnapshot.Entry> top = entries.subList(0, Math.min(n, entries.size()));
		Map<Long, LeaderboardRepository.ProfileRow> profiles = profiles(top);
		List<LeaderboardDTO.Row> rows = top.stream().map(e -> {
			LeaderboardRepository.ProfileRow p = profiles.get(e.userId());
			return new LeaderboardDTO.Row(e.rank(), p == null ? FALLBACK_NAME : displayName(p.getFirstName(), p.getLastName()),
					p == null ? null : avatarUrlService.resolve(p.getAvatarKey()),
					p == null ? null : avatarUrlService.resolve(p.getFrameKey()), e.score(), rankChange(baseline, e),
					e.userId() == userId);
		}).toList();

		int index = eligible ? LeaderboardRanking.indexOf(entries, userId) : -1;
		LeaderboardDTO.Me me;
		if (index >= 0) {
			LeaderboardSnapshot.Entry mine = entries.get(index);
			me = new LeaderboardDTO.Me(true, mine.rank(), mine.score(), rankChange(baseline, mine),
					LeaderboardRanking.xpToNextRank(entries, index), LeaderboardRanking.nextRank(entries, index));
		} else {
			me = new LeaderboardDTO.Me(eligible, null, 0, null, null, null);
		}
		return new LeaderboardDTO(window.weekStart(), window.weekEnd(), ProgressService.ZONE.getId(), window.endsAt(),
				snapshot.complete(), snapshot.computedAt(), entries.size(), rows, me);
	}

	public AdminLeaderboardDTO forAdmin(LeaderboardWeek week) {
		Instant now = clock.instant();
		LeaderboardWindow window = window(week, now);
		LeaderboardSnapshot snapshot = snapshots.get(window);
		Map<Long, Integer> baseline = baselineRanks(week, now);
		Map<Long, LeaderboardRepository.ProfileRow> profiles = profiles(snapshot.entries());
		List<AdminLeaderboardDTO.Row> rows = snapshot.entries().stream().map(e -> {
			LeaderboardRepository.ProfileRow p = profiles.get(e.userId());
			return new AdminLeaderboardDTO.Row(e.rank(), e.tied(), e.userId(),
					p == null ? FALLBACK_NAME : displayName(p.getFirstName(), p.getLastName()),
					p == null ? null : p.getEmail(), e.score(), e.reachedAt(), e.reviewXp(), e.quizXp(), e.bonusXp(),
					e.events(), e.activeDays(), e.peakHourXp(), rankChange(baseline, e));
		}).toList();
		long totalXp = snapshot.entries().stream().mapToLong(LeaderboardSnapshot.Entry::score).sum();
		return new AdminLeaderboardDTO(window.weekStart(), window.weekEnd(), ProgressService.ZONE.getId(),
				window.endsAt(), snapshot.complete(), snapshot.computedAt(),
				List.copyOf(LeaderboardSnapshotService.COUNTED_SOURCES), snapshot.eligibleLearners(), rows.size(),
				totalXp, rows);
	}

	/** LB-11: "first last", trimmed; never the email. */
	static String displayName(String firstName, String lastName) {
		String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
		return name.isEmpty() ? FALLBACK_NAME : name;
	}

	/** LB-08: rank at 00:00 today − rank now; null when absent at 00:00 (and always for PREVIOUS / Monday). */
	static Integer rankChange(Map<Long, Integer> baseline, LeaderboardSnapshot.Entry entry) {
		Integer before = baseline.get(entry.userId());
		return before == null ? null : before - entry.rank();
	}

	private static LeaderboardWindow window(LeaderboardWeek week, Instant now) {
		return week == LeaderboardWeek.PREVIOUS ? LeaderboardWindow.previousWeek(now) : LeaderboardWindow.live(now);
	}

	private Map<Long, Integer> baselineRanks(LeaderboardWeek week, Instant now) {
		if (week != LeaderboardWeek.CURRENT) {
			return Map.of();
		}
		return LeaderboardWindow.baseline(now).map(snapshots::get)
				.map(s -> s.entries().stream().collect(
						Collectors.toMap(LeaderboardSnapshot.Entry::userId, LeaderboardSnapshot.Entry::rank)))
				.orElse(Map.of());
	}

	private Map<Long, LeaderboardRepository.ProfileRow> profiles(List<LeaderboardSnapshot.Entry> entries) {
		List<Long> ids = entries.stream().map(LeaderboardSnapshot.Entry::userId).toList();
		Map<Long, LeaderboardRepository.ProfileRow> byId = new HashMap<>();
		for (int i = 0; i < ids.size(); i += PROFILE_BATCH) {
			List<Long> batch = new ArrayList<>(ids.subList(i, Math.min(i + PROFILE_BATCH, ids.size())));
			byId.putAll(repository.profiles(batch).stream()
					.collect(Collectors.toMap(LeaderboardRepository.ProfileRow::getId, Function.identity())));
		}
		return byId;
	}
}
