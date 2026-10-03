package vn.ptit.snapvocab.progress.domain.dto;

import java.time.LocalDate;
import java.util.List;
import vn.ptit.snapvocab.progress.domain.enumeration.HistoryPeriod;

public record ProgressHistoryDTO(HistoryPeriod period, LocalDate from, LocalDate to, List<Bucket> buckets) {

	public record Bucket(LocalDate periodStart, long eventCount, int activeDays) {
	}
}
