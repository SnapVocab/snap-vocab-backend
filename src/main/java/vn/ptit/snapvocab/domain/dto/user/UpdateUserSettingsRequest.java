package vn.ptit.snapvocab.domain.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;
import vn.ptit.snapvocab.domain.enumeration.ThemePreference;

/**
 * Partial update: every field is optional, and null leaves the stored value
 * alone. Turning the reminder off therefore needs {@code srsReminderCleared}
 * rather than a null time.
 */
public record UpdateUserSettingsRequest(
		@Pattern(regexp = "vi|en", message = "uiLanguage must be vi or en")
		String uiLanguage,

		ThemePreference theme,

		@Min(value = 1, message = "dailyGoal must be at least 1")
		@Max(value = 500, message = "dailyGoal must be at most 500")
		Integer dailyGoal,

		@JsonFormat(pattern = "HH:mm")
		LocalTime srsReminderAt,

		Boolean srsReminderCleared,

		Boolean pushEnabled
) {
}
