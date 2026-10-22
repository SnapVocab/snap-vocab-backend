package vn.ptit.snapvocab.mission.domain.enumeration;

import java.util.Set;

/** Developer-controlled producers (daily_mission.md §6.1); each template listens to exactly one. */
public enum MissionTriggerEvent {
	QUIZ_COMPLETED,
	LEARNING_ACTIVITY_RECORDED,
	TOPIC_ITEM_CREATED,
	SHOP_ITEM_PURCHASED,
	BOOSTER_USED,
	/** A card's first rating of the study day, new or due (same once-per-card-day rule as review XP, PD-DM-10). */
	SRS_REVIEW_COMPLETED,
	/** A quiz-ready word saved with the id of the learner's own finished scan that detected it (§6.1). */
	SCAN_WORD_SAVED;

	/**
	 * What an app that sends no {@code X-Mission-Triggers} header can render: every build before PD-DM-13.
	 * The catalog invariant is checked against these alone, so such an app always gets 5 missions.
	 */
	public static final Set<MissionTriggerEvent> LEGACY = Set.of(QUIZ_COMPLETED, LEARNING_ACTIVITY_RECORDED,
			TOPIC_ITEM_CREATED, SHOP_ITEM_PURCHASED, BOOSTER_USED);
}
