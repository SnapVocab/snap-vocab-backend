package vn.ptit.snapvocab.user.domain.dto.user;

import java.time.LocalTime;
import vn.ptit.snapvocab.user.domain.enumeration.ThemePreference;

/**
 * Settings shown on MH-PROFILE-03.
 *
 * @param srsReminderAt local time of the daily review reminder ("HH:mm"), null when off
 */
public record UserSettingsDTO(
		String uiLanguage,
		ThemePreference theme,
		Integer dailyGoal,
		LocalTime srsReminderAt,
		boolean pushEnabled
) {
}

