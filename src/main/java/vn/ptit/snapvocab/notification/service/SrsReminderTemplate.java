package vn.ptit.snapvocab.notification.service;

/**
 * SRS_REMINDER copy (notification.md §2.2, COPY-NOTIF-01 awaiting PO): rendered once at creation, in the learner's
 * ui_language at that moment. Template lives in code, not in a table.
 */
public final class SrsReminderTemplate {

	static final int MAX_FIRST_NAME = 20;
	static final int DUE_CAP = 99;

	public record Rendered(String title, String body) {
	}

	private SrsReminderTemplate() {
	}

	/** {@code language} other than "en" (or missing) renders Vietnamese. */
	public static Rendered render(String language, String firstName, long dueCount) {
		String name = firstName == null ? "" : firstName.strip();
		boolean named = !name.isEmpty() && name.length() <= MAX_FIRST_NAME; // never cut a name in half
		String count = dueCount > DUE_CAP ? DUE_CAP + "+" : String.valueOf(dueCount);
		if ("en".equals(language)) {
			String words = count + (dueCount == 1 ? " word" : " words");
			String body = named ? name + ", you have " + words + " to review. Review now to remember them longer!"
					: "You have " + words + " to review. Review now to remember them longer!";
			return new Rendered("Time to review!", body);
		}
		String body = (named ? name + " ơi, bạn có " : "Bạn có ") + count + " từ cần ôn. Ôn ngay để nhớ lâu hơn nhé!";
		return new Rendered("Đến giờ ôn tập rồi!", body);
	}
}
