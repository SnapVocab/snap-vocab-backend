package vn.ptit.snapvocab.user.service;

import vn.ptit.snapvocab.user.domain.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

		return existing.isPresent() ? settings : userSettingsRepository.save(settings);
	}

	private UserSettingsDTO toDto(UserSettings settings) {
		return new UserSettingsDTO(
				settings.getSrsReminderAt(),
				settings.isPushEnabled());
	}
}
