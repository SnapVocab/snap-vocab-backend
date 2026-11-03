package vn.ptit.snapvocab.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;

/** One message, already rendered; delivered to each recipient through {@link UserNotification} (notification.md §4.1). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications",
		uniqueConstraints = @UniqueConstraint(name = "uq_notifications_dedupe_key", columnNames = "dedupe_key"),
		indexes = @Index(name = "idx_notifications_created_at", columnList = "created_at"))
public class Notification extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String title;

	/** Plain text; the app shows it verbatim. */
	@Lob
	@Column(nullable = false, length = Length.LONG32)
	private String content;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	/** Screen opened on tap: null or a whitelisted route (§2.3). */
	@Column(length = 100)
	private String route;

	/** UNIQUE: what keeps concurrent jobs and repeated admin calls from creating a second copy (§5.1). */
	@Column(name = "dedupe_key", length = 120)
	private String dedupeKey;

	/** Same format as the NOT EXISTS in {@code NotificationRepository#findSrsReminderCandidates}. */
	public static String srsReminderDedupeKey(Long userId, LocalDate dayVn) {
		return "SRS_REMINDER:" + userId + ":" + dayVn;
	}

	/** Same format as the NOT EXISTS in {@code NotificationRepository#findStreakWarningCandidates}. */
	public static String streakWarningDedupeKey(Long userId, LocalDate dayVn) {
		return "STREAK_WARNING:" + userId + ":" + dayVn;
	}

	public static String systemDedupeKey(String key) {
		return "SYSTEM:" + key;
	}
}
