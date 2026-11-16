package vn.ptit.snapvocab.adminusers.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import vn.ptit.snapvocab.common.dto.Meta;

/** Contract of snapAdmin domains/admin-learners/types.ts; times are ISO-8601 UTC strings. */
public final class AdminUserDTOs {

	private AdminUserDTOs() {
	}

	/** ACTIVE = email verified; PENDING = never verified; BANNED = locked by an admin. */
	public enum Status {
		ACTIVE, PENDING, BANNED
	}

	public enum Activity {
		ACTIVE_7D, DORMANT_30D, NEVER
	}

	public record Row(long id, String displayName, String email, String avatarUrl, Status status, String createdAt,
			String lastStudiedAt, long streakDays, long longestStreakDays, long wordsSaved, long coin, long exp) {}

	public record Summary(long total, long active7d, long dormant30d, long never, long pending, long banned) {}

	public record ListResponse(List<Row> data, Meta meta, Summary summary) {}

	public record Ban(String reason, String at, String by) {}

	public record Progress(long newCount, long learning, long reviewing, long mastered, long dueToday,
			long quizzesCompleted, Long quizAccuracy) {}

	public record Missions(long assigned, long completed, long claimed) {}

	public record Badge(String code, String name, String earnedAt) {}

	public record Coin(String at, long amount, String source, String note) {}

	public record Wallet(List<Coin> recent) {}

	public record Item(String code, String name, String type, boolean equipped, long quantity) {}

	public record Scan(String at, String status, String errorCode, List<String> labels, long savedWords) {}

	public record Scans(int today, int dailyLimit, long last30d, long failed30d, List<Scan> recent) {}

	public record Event(String at, String kind, String text) {}

	public record Detail(@JsonUnwrapped Row row, String nativeLanguage, String learningLanguage, Ban ban,
			Progress progress, List<Long> activity30d, Missions missions7d, List<Badge> badges, Wallet wallet,
			List<Item> items, Scans scans, List<Event> timeline) {}

	/** POST …/ban and …/unban; the reason is required to ban (10..500), optional to unban. */
	public record ReasonRequest(String reason) {}
}
