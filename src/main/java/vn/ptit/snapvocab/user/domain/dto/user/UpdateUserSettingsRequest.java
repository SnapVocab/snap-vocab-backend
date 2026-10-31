package vn.ptit.snapvocab.user.domain.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;

/**
 * Partial update: every field is optional, and null leaves the stored value
 * alone. Turning the reminder off therefore needs {@code srsReminderCleared}
 * rather than a null time.
 */
// Older clients may still send these device-local fields; never persist them.
@JsonIgnoreProperties({"uiLanguage", "theme"})
public record UpdateUserSettingsRequest(
		@Min(value = 1, message = "dailyGoal must be at least 1")
		@Max(value = 500, message = "dailyGoal must be at most 500")
		Integer dailyGoal,

		@Min(value = 1, message = "studySessionCardLimit must be at least 1")
		@Max(value = 500, message = "studySessionCardLimit must be at most 500")
		Integer studySessionCardLimit,

		@JsonFormat(pattern = "HH:mm")
		LocalTime srsReminderAt,

		Boolean srsReminderCleared,

		Boolean pushEnabled
) {
}

