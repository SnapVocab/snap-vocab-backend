package vn.ptit.snapvocab.progress.domain.dto;

import java.time.LocalDate;

/** {@code freezeCount}: Streak Freeze units held; {@code lastFrozenDate}: last day one kept the chain (streak_freeze.md §6). */
public record StreakDTO(int currentStreak, int longestStreak, LocalDate lastStudiedDate, int freezeCount,
		LocalDate lastFrozenDate) {
}
