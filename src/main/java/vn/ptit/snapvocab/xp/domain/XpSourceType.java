package vn.ptit.snapvocab.xp.domain;

public enum XpSourceType {
	FLASHCARD_REVIEW(true),
	QUIZ(true),
	SAVE_WORD(false),
	MISSION_REWARD(false),
	ADMIN_ADJUSTMENT(false);

	/** XP Booster only multiplies learning activity (shop.md S6). */
	private final boolean boostable;

	XpSourceType(boolean boostable) {
		this.boostable = boostable;
	}

	public boolean isBoostable() {
		return boostable;
	}
}
