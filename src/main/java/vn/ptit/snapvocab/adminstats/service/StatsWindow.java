package vn.ptit.snapvocab.adminstats.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Calendar days of the Admin stats (admin_overview_backend.md §2.2): an N-day window is
 * {@code [today − (N − 1), today]}, and a day's instants are the half-open {@code [start, end)} in {@code zone}.
 */
public record StatsWindow(LocalDate today, ZoneId zone) {

	public static StatsWindow now(ZoneId zone) {
		return at(Instant.now(), zone);
	}

	public static StatsWindow at(Instant now, ZoneId zone) {
		return new StatsWindow(LocalDate.ofInstant(now, zone), zone);
	}

	/** First day of the {@code n}-day window ending today. */
	public LocalDate from(int n) {
		return today.minusDays(n - 1L);
	}

	public Instant start(LocalDate day) {
		return day.atStartOfDay(zone).toInstant();
	}

	public Instant end(LocalDate day) {
		return start(day.plusDays(1));
	}

	/** Every day of the {@code n}-day window, oldest first, as "yyyy-MM-dd". */
	public List<String> days(int n) {
		return from(n).datesUntil(today.plusDays(1)).map(LocalDate::toString).toList();
	}
}
