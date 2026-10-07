package vn.ptit.snapvocab.mission.domain.enumeration;

/** Stored states only; EXPIRED is derived ({@code mission_date < today ∧ status ≠ CLAIMED}, daily_mission.md §3.3). */
public enum DailyMissionStatus {
	IN_PROGRESS,
	COMPLETED,
	CLAIMED
}
