package vn.ptit.snapvocab.badge.domain.enumeration;

/** What a badge threshold is compared against (badges.md §3). */
public enum BadgeMetric {
	/** {@code users.longest_streak_days}; a study day has a completed quiz, an accepted SRS rating or a saved scan word. */
	LONGEST_STREAK_DAYS,
	/** Quizzes with status {@code COMPLETED}. */
	QUIZ_COMPLETED,
	/** Completed quizzes with {@code correct_count = total_units}. */
	PERFECT_QUIZ,
	/** Rows in {@code user_badge_word_credits}: distinct normalized words self-added (PD-BDG-03 = C). */
	DISTINCT_WORDS_ADDED
}
