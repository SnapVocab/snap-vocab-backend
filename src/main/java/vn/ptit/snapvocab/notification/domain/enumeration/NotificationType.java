package vn.ptit.snapvocab.notification.domain.enumeration;

/** notification.md §2. */
public enum NotificationType {
	/** Daily review reminder: in-app, plus push when enabled. */
	SRS_REMINDER,
	/** Admin message: in-app only. */
	SYSTEM,
	/** 21:00 warning that today's missed study day ends the streak or uses a Streak Freeze: in-app, plus push. */
	STREAK_WARNING
}
