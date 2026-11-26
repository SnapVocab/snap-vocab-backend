package vn.ptit.snapvocab.progress.domain.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * "Dự báo quên" on the stats screen (MH-STATS-01 extension).
 *
 * @param retentionNow average chance (0..1) of recalling the learner's started cards right now; null without cards
 * @param cardsTracked started (non-suspended) cards the projection covers
 */
public record ForecastDTO(List<Day> days, Double retentionNow, int cardsTracked, BestTime bestTime) {

	/**
	 * One of the next 7 study days (Asia/Ho_Chi_Minh). {@code dueCount} is the cards falling due that day (day 0 also
	 * holds overdue ones); {@code retention} is the average recall chance at the end of that day if nothing is reviewed.
	 */
	public record Day(LocalDate date, int dueCount, Double retention) {
	}

	/**
	 * When studying sticks best: reviews grouped into 4-hour slots by local start hour, each scored by whether the
	 * card was still remembered at its next review at least 12 hours later. {@code bestStartHour} is null until there
	 * are enough such pairs ({@code enoughData}).
	 */
	public record BestTime(boolean enoughData, int pairs, int minPairs, Integer bestStartHour, List<Slot> slots) {
	}

	/** {@code recallRate} (0..1) is null for a slot with too few reviews to judge. */
	public record Slot(int startHour, int reviews, Double recallRate) {
	}
}
