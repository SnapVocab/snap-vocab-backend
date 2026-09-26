package vn.ptit.snapvocab.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.user.domain.enumeration.ThemePreference;

/**
 * Per-user preferences shown on the Settings screen (MH-PROFILE-03). Kept apart
 * from {@code users} because the list keeps growing and none of it is needed
 * when loading a profile.
 *
 * <p>A row is created on first read, so every user has one without a migration
 * backfilling the whole table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_settings")
public class UserSettings extends BaseTimeEntity {

	/** Same value as {@link #user}'s id: one row per user. */
	@Id
	@Column(name = "user_id")
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	/** App interface language (i18n), not the language being learned. */
	@Builder.Default
	@Column(name = "ui_language", nullable = false, length = 10)
	private String uiLanguage = "vi";

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ThemePreference theme = ThemePreference.SYSTEM;

	/** Words the learner aims to study each day. */
	@Builder.Default
	@Column(name = "daily_goal", nullable = false)
	private Integer dailyGoal = 20;

	/** Local time for the daily review reminder; null means no reminder. */
	@Column(name = "srs_reminder_at")
	private LocalTime srsReminderAt;

	@Builder.Default
	@Column(name = "push_enabled", nullable = false)
	private boolean pushEnabled = true;
}

