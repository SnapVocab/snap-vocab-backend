package vn.ptit.snapvocab.progress.domain.dto;

public record HomeWidgetDTO(long totalWords, long learnedCount, long masteredCount, long dueCount, int currentStreak) {
}
