package vn.ptit.snapvocab.user.domain.dto.user;

import java.time.LocalTime;

/**
 * Settings shown on MH-PROFILE-03.
 *
 * @param srsReminderAt local time of the daily review reminder ("HH:mm"), null
 *                      when off
 */
public record UserSettingsDTO(
		LocalTime srsReminderAt,
		boolean pushEnabled) {
}
