package vn.ptit.snapvocab.shop.domain.enumeration;

public enum ShopItemType {
	THEME(Behavior.EQUIP, false),
	AVATAR_FRAME(Behavior.EQUIP, false),
	XP_BOOSTER(Behavior.ACTIVATE, true),
	STREAK_FREEZE(Behavior.PASSIVE, true);

	/** XP Booster multiplier is a developer constant, not item config (shop.md §2.3). */
	public static final int XP_BOOSTER_MULTIPLIER = 2;

	private final Behavior behavior;
	private final boolean stackable;

	ShopItemType(Behavior behavior, boolean stackable) {
		this.behavior = behavior;
		this.stackable = stackable;
	}

	public Behavior getBehavior() {
		return behavior;
	}

	public boolean isStackable() {
		return stackable;
	}

	public boolean isEquippable() {
		return behavior == Behavior.EQUIP;
	}

	public boolean isActivatable() {
		return behavior == Behavior.ACTIVATE;
	}
}
