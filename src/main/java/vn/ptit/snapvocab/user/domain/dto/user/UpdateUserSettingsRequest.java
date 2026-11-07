package vn.ptit.snapvocab.user.domain.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalTime;

/**
 * Partial update: every field is optional, and null leaves the stored value
 * alone. Turning the reminder off therefore needs {@code srsReminderCleared}
 * rather than a null time.
 */
// Older clients may still send removed preferences; never persist or apply them.
@JsonIgnoreProperties({"uiLanguage", "theme", "dailyGoal", "studySessionCardLimit"})
public record UpdateUserSettingsRequest(
		@JsonFormat(pattern = "HH:mm")
		LocalTime srsReminderAt,

		Boolean srsReminderCleared,

		Boolean pushEnabled
) {
}

