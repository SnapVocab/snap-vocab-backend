package vn.ptit.snapvocab.progress.domain.dto;

public record ProgressSummaryDTO(
		long totalWords,
		long learnedCount,
		long reviewingCount,
		long masteredCount,
		long dueCount,
		Double accuracy,
		long reviewCount,
		long completedQuizCount) {
}
