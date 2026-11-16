package vn.ptit.snapvocab.scan.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.notification.domain.Notification;
import vn.ptit.snapvocab.notification.domain.UserNotification;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;
import vn.ptit.snapvocab.notification.repository.DeviceTokenRepository;
import vn.ptit.snapvocab.notification.repository.NotificationRepository;
import vn.ptit.snapvocab.notification.repository.UserNotificationRepository;
import vn.ptit.snapvocab.notification.service.PushSender;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.repository.UserSettingsRepository;

/**
 * Persists an inbox notification for a completed scan and sends an Expo push
 * notification via {@link PushSender} to the learner's registered devices.
 */
@Slf4j
@Service
public class ScanNotificationService {

	private final NotificationRepository notificationRepository;
	private final UserNotificationRepository userNotificationRepository;
	private final DeviceTokenRepository deviceTokenRepository;
	private final UserRepository userRepository;
	private final UserSettingsRepository userSettingsRepository;
	private final PushSender pushSender;
	private final TransactionTemplate transaction;

	public ScanNotificationService(NotificationRepository notificationRepository,
			UserNotificationRepository userNotificationRepository, DeviceTokenRepository deviceTokenRepository,
			UserRepository userRepository, UserSettingsRepository userSettingsRepository,
			PushSender pushSender, PlatformTransactionManager transactionManager) {
		this.notificationRepository = notificationRepository;
		this.userNotificationRepository = userNotificationRepository;
		this.deviceTokenRepository = deviceTokenRepository;
		this.userRepository = userRepository;
		this.userSettingsRepository = userSettingsRepository;
		this.pushSender = pushSender;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public void notifyScanCompleted(ScanRequest job) {
		if (job == null || job.getUser() == null || job.getUser().getId() == null) {
			return;
		}
		Long userId = job.getUser().getId();
		int count = job.getDetectionCount() != null ? job.getDetectionCount() : 0;
		String title = count > 0 ? "Quét ảnh hoàn tất!" : "Quét ảnh hoàn tất";
		String content = count > 0
				? "Đã nhận diện được " + count + " từ vựng từ ảnh của bạn. Nhấn để xem kết quả."
				: "Ảnh đã xử lý xong nhưng chưa nhận diện được đồ vật nào.";
		String route = "/scan-result?requestId=" + job.getId();
		String dedupeKey = "SCAN_RESULT:" + job.getId();

		UserNotification saved;
		try {
			saved = transaction.execute(status -> {
				Notification notification = notificationRepository.saveAndFlush(Notification.builder()
						.type(NotificationType.SYSTEM)
						.title(title)
						.content(content)
						.route(route)
						.dedupeKey(dedupeKey)
						.build());
				return userNotificationRepository.save(UserNotification.builder()
						.user(userRepository.getReferenceById(userId))
						.notification(notification)
						.build());
			});
		} catch (DataIntegrityViolationException e) {
			log.debug("Scan notification {} already created, skipping duplicate", dedupeKey);
			return;
		}

		if (saved == null) {
			return;
		}
		if (userSettingsRepository.findById(userId).filter(s -> !s.isPushEnabled()).isPresent()) {
			return;
		}
		List<String> tokens = deviceTokenRepository.findTokensByUserId(userId);
		if (tokens.isEmpty()) {
			return;
		}
		int unread = (int) userNotificationRepository.countByUserIdAndIsReadFalse(userId);
		Map<String, Object> data = Map.of(
				"userNotificationId", saved.getId(),
				"userId", userId,
				"type", NotificationType.SYSTEM.name(),
				"route", route,
				"requestId", job.getId());
		List<PushSender.PushMessage> messages = tokens.stream()
				.map(token -> new PushSender.PushMessage(token, title, content, data, unread))
				.toList();
		pushSender.send(messages);
	}
}
