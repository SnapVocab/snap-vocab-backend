package vn.ptit.snapvocab.leaderboard.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import vn.ptit.snapvocab.progress.service.ProgressService;

/** A ranking window and its Redis key (leaderboard_backend.md §5.1). {@code cutoff == null} ⇒ live. */
public record LeaderboardWindow(LocalDate weekStart, Instant start, Instant end, LocalDate cutoff) {

	/** XP written just before a cutoff can commit a few seconds later; a closed window is final this long after. */
	public static final Duration GRACE = Duration.ofMinutes(2);
	private static final ZoneId ZONE = ProgressService.ZONE;

	public static LeaderboardWindow live(Instant now) {
		LocalDate weekStart = weekStartOf(now);
		return new LeaderboardWindow(weekStart, startOf(weekStart), startOf(weekStart.plusDays(7)), null);
	}

	public static LeaderboardWindow previousWeek(Instant now) {
		LocalDate weekStart = weekStartOf(now);
		return closed(weekStart.minusDays(7), weekStart);
	}

	/** Standings at 00:00 today, the trend baseline (LB-08). Empty on Monday: the week has no past yet. */
	public static Optional<LeaderboardWindow> baseline(Instant now) {
		LocalDate today = LocalDate.ofInstant(now, ZONE);
		LocalDate weekStart = weekStartOf(now);
		return today.equals(weekStart) ? Optional.empty() : Optional.of(closed(weekStart, today));
	}

	private static LeaderboardWindow closed(LocalDate weekStart, LocalDate cutoff) {
		return new LeaderboardWindow(weekStart, startOf(weekStart), startOf(cutoff), cutoff);
	}

	static LocalDate weekStartOf(Instant instant) {
		return LocalDate.ofInstant(instant, ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	private static Instant startOf(LocalDate day) {
		return day.atStartOfDay(ZONE).toInstant();
	}

	public boolean live() {
		return cutoff == null;
	}

	public String key() {
		return "leaderboard:v1:" + weekStart + (live() ? ":live" : ":upto:" + cutoff);
	}

	public Duration ttl() {
		return live() ? Duration.ofMinutes(15) : Duration.ofDays(8);
	}

	public boolean completeAt(Instant computedAt) {
		return !live() && !computedAt.isBefore(end.plus(GRACE));
	}

	public LocalDate weekEnd() {
		return weekStart.plusDays(6);
	}

	public Instant endsAt() {
		return startOf(weekStart.plusDays(7));
	}
}
