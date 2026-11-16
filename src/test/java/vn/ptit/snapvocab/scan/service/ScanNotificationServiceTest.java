package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import vn.ptit.snapvocab.notification.domain.Notification;
import vn.ptit.snapvocab.notification.domain.UserNotification;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;
import vn.ptit.snapvocab.notification.repository.DeviceTokenRepository;
import vn.ptit.snapvocab.notification.repository.NotificationRepository;
import vn.ptit.snapvocab.notification.repository.UserNotificationRepository;
import vn.ptit.snapvocab.notification.service.PushSender;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.UserSettings;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.repository.UserSettingsRepository;

@ExtendWith(MockitoExtension.class)
class ScanNotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;
	@Mock
	private UserNotificationRepository userNotificationRepository;
	@Mock
	private DeviceTokenRepository deviceTokenRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private UserSettingsRepository userSettingsRepository;
	@Mock
	private PushSender pushSender;
	@Mock
	private PlatformTransactionManager transactionManager;

	private ScanNotificationService service;
	private User user;
	private ScanRequest job;

	@BeforeEach
	void setUp() {
		service = new ScanNotificationService(
				notificationRepository, userNotificationRepository, deviceTokenRepository,
				userRepository, userSettingsRepository, pushSender, transactionManager);
		user = User.builder().id(7L).firstName("Thanh").password("x".repeat(60)).build();
		job = ScanRequest.builder()
				.id("req-1")
				.user(user)
				.status(ScanStatus.DONE)
				.detectionCount(4)
				.build();
		lenient().when(userRepository.getReferenceById(7L)).thenReturn(user);
	}

	@Test
	@SuppressWarnings("unchecked")
	void notifyScanCompletedPersistsInboxRowAndSendsExpoPush() {
		when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userNotificationRepository.save(any(UserNotification.class))).thenAnswer(inv -> {
			UserNotification un = inv.getArgument(0);
			un.setId(55L);
			return un;
		});
		when(userSettingsRepository.findById(7L)).thenReturn(Optional.empty());
		when(deviceTokenRepository.findTokensByUserId(7L)).thenReturn(List.of("ExponentPushToken[abc]"));
		when(userNotificationRepository.countByUserIdAndIsReadFalse(7L)).thenReturn(3L);

		service.notifyScanCompleted(job);

		ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
		verify(notificationRepository).saveAndFlush(notifCaptor.capture());
		Notification savedNotif = notifCaptor.getValue();
		assertThat(savedNotif.getType()).isEqualTo(NotificationType.SYSTEM);
		assertThat(savedNotif.getDedupeKey()).isEqualTo("SCAN_RESULT:req-1");
		assertThat(savedNotif.getRoute()).isEqualTo("/scan-result?requestId=req-1");
		assertThat(savedNotif.getContent()).contains("4");

		ArgumentCaptor<List<PushSender.PushMessage>> pushCaptor = ArgumentCaptor.forClass(List.class);
		verify(pushSender).send(pushCaptor.capture());
		assertThat(pushCaptor.getValue()).hasSize(1);
		PushSender.PushMessage msg = pushCaptor.getValue().get(0);
		assertThat(msg.to()).isEqualTo("ExponentPushToken[abc]");
		assertThat(msg.title()).isEqualTo("Quét ảnh hoàn tất!");
		assertThat(msg.body()).contains("4");
		assertThat(msg.badge()).isEqualTo(3);
		assertThat(msg.data())
				.containsEntry("userNotificationId", 55L)
				.containsEntry("userId", 7L)
				.containsEntry("type", NotificationType.SYSTEM.name())
				.containsEntry("route", "/scan-result?requestId=req-1")
				.containsEntry("requestId", "req-1");
	}

	@Test
	void notifyScanCompletedSkipsPushWhenPushDisabled() {
		when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userNotificationRepository.save(any(UserNotification.class))).thenAnswer(inv -> {
			UserNotification un = inv.getArgument(0);
			un.setId(55L);
			return un;
		});
		when(userSettingsRepository.findById(7L)).thenReturn(
				Optional.of(UserSettings.builder().userId(7L).user(user).pushEnabled(false).build()));

		service.notifyScanCompleted(job);

		verify(notificationRepository).saveAndFlush(any(Notification.class));
		verify(userNotificationRepository).save(any(UserNotification.class));
		verifyNoInteractions(deviceTokenRepository, pushSender);
	}

	@Test
	void notifyScanCompletedIgnoresDuplicateDedupeKey() {
		when(notificationRepository.saveAndFlush(any(Notification.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate dedupe_key"));

		service.notifyScanCompleted(job);

		verifyNoInteractions(userNotificationRepository, userSettingsRepository, deviceTokenRepository, pushSender);
	}
}
