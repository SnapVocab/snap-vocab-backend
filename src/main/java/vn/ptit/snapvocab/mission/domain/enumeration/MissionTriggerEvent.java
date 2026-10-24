package vn.ptit.snapvocab.mission.domain.enumeration;

/**
 * Developer-controlled producers (daily_mission.md §6.1); each template listens
 * to exactly one.
 */
public enum MissionTriggerEvent {
	QUIZ_COMPLETED,
	LEARNING_ACTIVITY_RECORDED,
	TOPIC_ITEM_CREATED,
	SHOP_ITEM_PURCHASED,
	BOOSTER_USED,
	SRS_REVIEW_COMPLETED,
	SCAN_WORD_SAVED
}
