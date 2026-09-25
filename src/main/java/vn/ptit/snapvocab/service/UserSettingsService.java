package vn.ptit.snapvocab.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.UserSettings;
import vn.ptit.snapvocab.domain.dto.user.UpdateUserSettingsRequest;
import vn.ptit.snapvocab.domain.dto.user.UserSettingsDTO;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.repository.UserSettingsRepository;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

/** Settings screen (MH-PROFILE-03). Defaults are created on first read. */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

	private final UserSettingsRepository userSettingsRepository;
	private final UserRepository userRepository;

	@Transactional
	public UserSettingsDTO get(Long userId) {
		return toDto(getOrCreate(userId));
	}

	/**
	 * Partial update: a null field keeps the stored value, so the app can send
	 * only what changed. The reminder is switched off with
	 * {@code srsReminderCleared}, because null already means "unchanged".
	 */
	@Transactional
	public UserSettingsDTO update(Long userId, UpdateUserSettingsRequest request) {
		UserSettings settings = getOrCreate(userId);

		if (request.uiLanguage() != null) {
			settings.setUiLanguage(request.uiLanguage());
		}
		if (request.theme() != null) {
			settings.setTheme(request.theme());
		}
		if (request.dailyGoal() != null) {
			settings.setDailyGoal(request.dailyGoal());
		}
		if (Boolean.TRUE.equals(request.srsReminderCleared())) {
			settings.setSrsReminderAt(null);
		} else if (request.srsReminderAt() != null) {
			settings.setSrsReminderAt(request.srsReminderAt());
		}
		if (request.pushEnabled() != null) {
			settings.setPushEnabled(request.pushEnabled());
		}

		return toDto(userSettingsRepository.save(settings));
	}

	private UserSettings getOrCreate(Long userId) {
		return userSettingsRepository.findById(userId)
				.orElseGet(() -> userSettingsRepository.save(UserSettings.builder()
						.user(userRepository.findById(userId)
								.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId)))
						.build()));
	}

	private UserSettingsDTO toDto(UserSettings settings) {
		return new UserSettingsDTO(
				settings.getUiLanguage(),
				settings.getTheme(),
				settings.getDailyGoal(),
				settings.getSrsReminderAt(),
				settings.isPushEnabled());
	}
}
