package vn.ptit.snapvocab.user.service;

import vn.ptit.snapvocab.user.domain.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import vn.ptit.snapvocab.user.domain.UserSettings;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateUserSettingsRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserSettingsDTO;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.repository.UserSettingsRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

/** Settings screen (MH-PROFILE-03). Defaults are created on first read. */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

	private static final int DEFAULT_STUDY_SESSION_CARD_LIMIT = 20;

	private final UserSettingsRepository userSettingsRepository;
	private final UserRepository userRepository;

	@Transactional
	public UserSettingsDTO get(Long userId) {
		return toDto(getOrCreate(userId));
	}

	@Transactional
	@Cacheable(cacheNames = "user-study-session-limit", key = "#userId")
	public int getStudySessionCardLimit(Long userId) {
		return getOrCreate(userId).getStudySessionCardLimit();
	}

	/**
	 * Partial update: a null field keeps the stored value, so the app can send
	 * only what changed. The reminder is switched off with
	 * {@code srsReminderCleared}, because null already means "unchanged".
	 */
	@Transactional
	@CacheEvict(cacheNames = "user-study-session-limit", key = "#userId")
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
		if (request.studySessionCardLimit() != null) {
			settings.setStudySessionCardLimit(request.studySessionCardLimit());
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
		var existing = userSettingsRepository.findById(userId);
		UserSettings settings;
		if (existing.isPresent()) {
			settings = existing.get();
		} else {
			User user = userRepository.findById(userId)
					.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
			settings = new UserSettings();
			settings.setUser(user);
		}

		// Hibernate's schema update may populate a newly added NOT NULL integer column with 0
		// for existing rows. Repair that legacy value before exposing or using the settings.
		if (settings.getStudySessionCardLimit() == null || settings.getStudySessionCardLimit() < 1) {
			settings.setStudySessionCardLimit(DEFAULT_STUDY_SESSION_CARD_LIMIT);
			return userSettingsRepository.save(settings);
		}
		return existing.isPresent() ? settings : userSettingsRepository.save(settings);
	}

	private UserSettingsDTO toDto(UserSettings settings) {
		return new UserSettingsDTO(
				settings.getUiLanguage(),
				settings.getTheme(),
				settings.getDailyGoal(),
				settings.getStudySessionCardLimit(),
				settings.getSrsReminderAt(),
				settings.isPushEnabled());
	}
}
