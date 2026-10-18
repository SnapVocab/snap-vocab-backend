package vn.ptit.snapvocab.progress.domain.dto;

import java.time.LocalDate;

public record StreakDTO(int currentStreak, int longestStreak, LocalDate lastStudiedDate) {
}
