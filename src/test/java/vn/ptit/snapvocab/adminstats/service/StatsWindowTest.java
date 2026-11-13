package vn.ptit.snapvocab.adminstats.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.progress.service.ProgressService;

/** admin_overview_backend.md D-01. */
class StatsWindowTest {

	private final StatsWindow w = new StatsWindow(LocalDate.parse("2026-10-19"), ProgressService.ZONE);

	@Test
	void windowsEndTodayOldestFirst() {
		assertThat(w.days(14)).hasSize(14).startsWith("2026-10-06").endsWith("2026-10-19");
		assertThat(w.from(7)).isEqualTo(LocalDate.parse("2026-10-13"));
		assertThat(w.from(1)).isEqualTo(w.today());
	}

	@Test
	void vietnamDayIsFrom17hUtcTheDayBefore() {
		assertThat(w.start(w.today())).isEqualTo(Instant.parse("2026-10-18T17:00:00Z"));
		assertThat(w.end(w.today())).isEqualTo(Instant.parse("2026-10-19T17:00:00Z"));
	}
}
