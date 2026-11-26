package vn.ptit.snapvocab.audit.domain.enumeration;

/**
 * What was done; carries its area, target type and "lifecycle" flag so a caller cannot pass them wrong (§3.3).
 * Adding a value means a migration that widens chk_aal_action.
 */
public enum AuditAction {
	SHOP_ITEM_CREATED(AuditArea.SHOP, AuditTargetType.SHOP_ITEM, true),
	SHOP_ITEM_UPDATED(AuditArea.SHOP, AuditTargetType.SHOP_ITEM, false),
	SHOP_ITEM_PUBLISHED(AuditArea.SHOP, AuditTargetType.SHOP_ITEM, true),
	SHOP_ITEM_ARCHIVED(AuditArea.SHOP, AuditTargetType.SHOP_ITEM, true),
	SHOP_ITEM_DELETED(AuditArea.SHOP, AuditTargetType.SHOP_ITEM, true),
	MISSION_TEMPLATE_CREATED(AuditArea.MISSIONS, AuditTargetType.MISSION_TEMPLATE, true),
	MISSION_TEMPLATE_UPDATED(AuditArea.MISSIONS, AuditTargetType.MISSION_TEMPLATE, false),
	MISSION_TEMPLATE_ENABLED(AuditArea.MISSIONS, AuditTargetType.MISSION_TEMPLATE, true),
	MISSION_TEMPLATE_DISABLED(AuditArea.MISSIONS, AuditTargetType.MISSION_TEMPLATE, true),
	// Badges P2 (badges.md §8.4, OQ-BDGA-02): content edits and on/off; no create or delete before P3.
	BADGE_UPDATED(AuditArea.BADGES, AuditTargetType.BADGE, false),
	BADGE_ENABLED(AuditArea.BADGES, AuditTargetType.BADGE, true),
	BADGE_DISABLED(AuditArea.BADGES, AuditTargetType.BADGE, true),
	COLLECTION_CREATED(AuditArea.TOPICS, AuditTargetType.COLLECTION, true),
	COLLECTION_UPDATED(AuditArea.TOPICS, AuditTargetType.COLLECTION, false),
	COLLECTION_DELETED(AuditArea.TOPICS, AuditTargetType.COLLECTION, true),
	TOPIC_CREATED(AuditArea.TOPICS, AuditTargetType.TOPIC, true),
	TOPIC_UPDATED(AuditArea.TOPICS, AuditTargetType.TOPIC, false),
	TOPIC_DELETED(AuditArea.TOPICS, AuditTargetType.TOPIC, true),
	TOPIC_HIDDEN(AuditArea.TOPICS, AuditTargetType.TOPIC, true),
	TOPIC_RESTORED(AuditArea.TOPICS, AuditTargetType.TOPIC, true),
	TOPIC_ITEM_ADDED(AuditArea.TOPICS, AuditTargetType.TOPIC, false),
	TOPIC_ITEM_UPDATED(AuditArea.TOPICS, AuditTargetType.TOPIC, false),
	TOPIC_ITEM_DELETED(AuditArea.TOPICS, AuditTargetType.TOPIC, false),
	TOPIC_TEMPLATE_CHANGED(AuditArea.TOPICS, AuditTargetType.TOPIC, false),
	TOPIC_SCHEMA_CHANGED(AuditArea.TOPICS, AuditTargetType.TOPIC, false),
	// Admin content v1 (migration_admin_content_v1.sql): template editing and the schema's default card template.
	CARD_TEMPLATE_UPDATED(AuditArea.TOPICS, AuditTargetType.CARD_TEMPLATE, false),
	CARD_TEMPLATE_DEFAULT_CHANGED(AuditArea.TOPICS, AuditTargetType.CARD_TEMPLATE, true),
	SYSTEM_NOTIFICATION_SENT(AuditArea.NOTIFICATIONS, AuditTargetType.NOTIFICATION, false),
	// M3: in the enum and the CHECK from the start, so those APIs need no table change.
	WORD_UPDATED(AuditArea.DICTIONARY, AuditTargetType.WORD, false),
	WORD_DELETED(AuditArea.DICTIONARY, AuditTargetType.WORD, true),
	WORD_RESTORED(AuditArea.DICTIONARY, AuditTargetType.WORD, true),
	SCAN_LABEL_MAPPED(AuditArea.AI_SCAN, AuditTargetType.SCAN_LABEL, false),
	LEARNER_BANNED(AuditArea.LEARNERS, AuditTargetType.LEARNER, false),
	LEARNER_UNBANNED(AuditArea.LEARNERS, AuditTargetType.LEARNER, false),
	LEARNER_PASSWORD_RESET_SENT(AuditArea.LEARNERS, AuditTargetType.LEARNER, false);

	private final AuditArea area;
	private final AuditTargetType targetType;
	/** "Vòng đời" cell of the summary strip. */
	private final boolean lifecycle;

	AuditAction(AuditArea area, AuditTargetType targetType, boolean lifecycle) {
		this.area = area;
		this.targetType = targetType;
		this.lifecycle = lifecycle;
	}

	public AuditArea getArea() {
		return area;
	}

	public AuditTargetType getTargetType() {
		return targetType;
	}

	public boolean isLifecycle() {
		return lifecycle;
	}

	/** Creating or deleting is not a "price/reward change" even with a price field (§5.3). */
	public boolean isCreateOrDelete() {
		return name().endsWith("_CREATED") || name().endsWith("_DELETED");
	}
}
