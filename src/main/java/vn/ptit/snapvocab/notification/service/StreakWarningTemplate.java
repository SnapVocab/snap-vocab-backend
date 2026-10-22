package vn.ptit.snapvocab.notification.service;

/**
 * STREAK_WARNING copy (notification.md §2.2, COPY-NOTIF-02): rendered once at creation, in the learner's ui_language
 * at that moment. {@code freezeLeft}: a Streak Freeze unit will cover today, so the chain is kept, not lost.
 */
public final class StreakWarningTemplate {

	private StreakWarningTemplate() {
	}

	/** {@code language} other than "en" (or missing) renders Vietnamese. */
	public static SrsReminderTemplate.Rendered render(String language, int streak, boolean freezeLeft) {
		if ("en".equals(language)) {
			String body = freezeLeft
					? "You haven't studied today. If you don't before midnight, 1 Streak Freeze will keep your chain."
					: "You haven't studied today. Study before midnight to keep your " + streak + "-day streak!";
			return new SrsReminderTemplate.Rendered("Keep your " + streak + "-day streak!", body);
		}
		String body = freezeLeft
				? "Bạn chưa học hôm nay. Nếu không học trước 0 giờ, 1 Đóng băng chuỗi sẽ được dùng để giữ chuỗi."
				: "Bạn chưa học hôm nay. Học trước 0 giờ để giữ chuỗi " + streak + " ngày nhé!";
		return new SrsReminderTemplate.Rendered("Giữ chuỗi " + streak + " ngày nhé!", body);
	}
}
