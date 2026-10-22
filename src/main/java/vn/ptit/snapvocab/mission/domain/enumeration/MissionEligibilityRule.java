package vn.ptit.snapvocab.mission.domain.enumeration;

/** daily_mission.md §4.3. */
public enum MissionEligibilityRule {
	NONE,
	HAS_IDLE_XP_BOOSTER,
	HAS_PURCHASABLE_ITEM,
	/**
	 * Today's study session (StudyService.getToday) holds at least the template's target in cards, so a
	 * "study N cards" mission can be finished today. The only rule that depends on the template.
	 */
	HAS_STUDY_CARDS_FOR_TARGET
}
