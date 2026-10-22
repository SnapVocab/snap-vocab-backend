package vn.ptit.snapvocab.shop.domain.enumeration;

public enum Behavior {
	EQUIP,
	ACTIVATE,
	/** Works on its own, never equipped or activated: STREAK_FREEZE (streak_freeze.md §6). */
	PASSIVE
}
