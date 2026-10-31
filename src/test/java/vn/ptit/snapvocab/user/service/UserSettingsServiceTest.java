package vn.ptit.snapvocab.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.UserSettings;
import vn.ptit.snapvocab.user.domain.dto.user.UpdateUserSettingsRequest;
import vn.ptit.snapvocab.user.domain.dto.user.UserSettingsDTO;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.repository.UserSettingsRepository;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

	private static final Long USER_ID = 7L;

	@Mock
	private UserSettingsRepository userSettingsRepository;
	@Mock
	private UserRepository userRepository;

	private UserSettingsService service;

	@BeforeEach
	void setUp() {
		service = new UserSettingsService(userSettingsRepository, userRepository);
		when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	private UserSettings stored() {
		UserSettings settings = UserSettings.builder().userId(USER_ID).build();
		when(userSettingsRepository.findById(USER_ID)).thenReturn(Optional.of(settings));
		return settings;
	}

	@Test
	void firstReadCreatesDefaults() {
		when(userSettingsRepository.findById(USER_ID)).thenReturn(Optional.empty());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));

		UserSettingsDTO settings = service.get(USER_ID);

		assertThat(settings).isEqualTo(
				new UserSettingsDTO(20, 20, null, true));
		verify(userSettingsRepository).save(any());
	}

	@Test
	void updateChangesOnlyTheFieldsSent() {
		UserSettings settings = stored();
		settings.setDailyGoal(50);

		UserSettingsDTO updated = service.update(USER_ID,
				new UpdateUserSettingsRequest(null, 40, null, null, null));

		assertThat(updated.dailyGoal()).isEqualTo(50);
		assertThat(updated.studySessionCardLimit()).isEqualTo(40);
	}

	@Test
	void updateSetsTheReminderTime() {
		stored();

		UserSettingsDTO updated = service.update(USER_ID, new UpdateUserSettingsRequest(
				30, null, LocalTime.of(20, 30), null, false));

		assertThat(updated.srsReminderAt()).isEqualTo(LocalTime.of(20, 30));
		assertThat(updated.dailyGoal()).isEqualTo(30);
		assertThat(updated.studySessionCardLimit()).isEqualTo(20);
		assertThat(updated.pushEnabled()).isFalse();
	}

	@Test
	void reminderIsClearedExplicitly() {
		UserSettings settings = stored();
		settings.setSrsReminderAt(LocalTime.of(7, 0));

		// A null time means "unchanged", so switching the reminder off needs the flag.
		assertThat(service.update(USER_ID,
				new UpdateUserSettingsRequest(null, null, null, null, null)).srsReminderAt())
				.isEqualTo(LocalTime.of(7, 0));
		assertThat(service.update(USER_ID,
				new UpdateUserSettingsRequest(null, null, null, true, null)).srsReminderAt())
				.isNull();
	}
}

