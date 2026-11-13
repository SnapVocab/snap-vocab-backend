package vn.ptit.snapvocab.adminstats.dto;

import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;

/** Records shared by the Dashboard and Analytics responses (admin_overview_backend.md §3.2). */
public final class StatsDTOs {

	private StatsDTOs() {
	}

	public record ScanConfig(int workers, int queueCapacity, int quotaPerDay, String minReliability) {}

	public record ScanDay(String date, long done, long failed) {}

	public record ErrorCount(ScanErrorCode code, long count) {}

	public record CoinDay(String date, long coinIn, long coinOut) {}
}
