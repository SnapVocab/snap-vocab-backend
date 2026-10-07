package vn.ptit.snapvocab.mission.domain.enumeration;

public enum MissionCategory {
	SCAN,
	VOCABULARY,
	FLASHCARD,
	SRS,
	QUIZ,
	RETENTION,
	SHOP;

	/** Slots 1–4 (daily_mission.md §2.2): the core learning groups a slot without candidates falls back to. */
	public boolean isCore() {
		return this != RETENTION && this != SHOP;
	}
}
