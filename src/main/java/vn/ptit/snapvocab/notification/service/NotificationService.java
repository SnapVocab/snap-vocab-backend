package vn.ptit.snapvocab.notification.service;

import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.notification.domain.Notification;
import vn.ptit.snapvocab.notification.domain.UserNotification;
import vn.ptit.snapvocab.notification.domain.dto.NotificationPageDTO;
import vn.ptit.snapvocab.notification.domain.dto.NotificationReadDTO;
import vn.ptit.snapvocab.notification.domain.dto.ReadAllResultDTO;
import vn.ptit.snapvocab.notification.domain.dto.SystemNotificationRequest;
import vn.ptit.snapvocab.notification.domain.dto.SystemNotificationResultDTO;
import vn.ptit.snapvocab.notification.domain.dto.UnreadCountDTO;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationErrorCode;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;
import vn.ptit.snapvocab.notification.exception.NotificationException;
import vn.ptit.snapvocab.notification.repository.NotificationRepository;
import vn.ptit.snapvocab.notification.repository.UserNotificationRepository;

/** Inbox (A1–A4) and admin SYSTEM messages (A8), notification.md §4.3. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	/** §2.3. The app re-checks against its own copy and treats unknown routes as null. */
	public static final Set<String> ROUTES = Set.of("/study/select", "/missions", "/shop", "/stats", "/settings");

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;

	private final NotificationRepository notificationRepository;
	private final UserNotificationRepository userNotificationRepository;

	/** {@code size} is clamped to [1, 50], never rejected. */
	@Transactional(readOnly = true)
	public NotificationPageDTO list(Long userId, Long cursor, Integer size) {
		int limit = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		List<UserNotification> rows = userNotificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId,
				cursor == null ? Long.MAX_VALUE : cursor, Limit.of(limit));
		Long nextCursor = rows.size() == limit ? rows.get(rows.size() - 1).getId() : null;
		return new NotificationPageDTO(rows.stream().map(NotificationService::toItem).toList(), nextCursor);
	}

	@Transactional(readOnly = true)
	public UnreadCountDTO unreadCount(Long userId) {
		return new UnreadCountDTO(userNotificationRepository.countByUserIdAndIsReadFalse(userId));
	}

	/** Idempotent: an already read row keeps its first readAt. */
	@Transactional
	public NotificationReadDTO markRead(Long userId, Long id) {
		UserNotification row = userNotificationRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND,
						"Notification " + id + " not found"));
		if (!Boolean.TRUE.equals(row.getIsRead())) {
			row.setIsRead(true);
			row.setReadAt(now());
		}
		return new NotificationReadDTO(row.getId(), true, row.getReadAt());
	}

	@Transactional
	public ReadAllResultDTO markAllRead(Long userId) {
		return new ReadAllResultDTO(userNotificationRepository.markAllRead(userId, now()));
	}

	/** One transaction: the message, then an inbox row for every user activated right now. No push (OQ-NOTIF-03). */
	@Transactional
	public SystemNotificationResultDTO createSystem(SystemNotificationRequest request) {
		if (request.route() != null && !ROUTES.contains(request.route())) {
			throw new NotificationException(NotificationErrorCode.ROUTE_NOT_ALLOWED, "Route not allowed: " + request.route());
		}
		Notification saved;
		try {
			saved = notificationRepository.saveAndFlush(Notification.builder()
					.type(NotificationType.SYSTEM)
					.title(request.title())
					.content(request.content())
					.route(request.route())
					.dedupeKey(Notification.systemDedupeKey(request.dedupeKey()))
					.build());
		} catch (DataIntegrityViolationException e) {
			Metrics.counter("notification.deduped", "type", NotificationType.SYSTEM.name()).increment();
			throw new NotificationException(NotificationErrorCode.DUPLICATE_NOTIFICATION,
					"dedupeKey already used: " + request.dedupeKey());
		}
		int recipients = userNotificationRepository.fanOut(saved.getId());
		Metrics.counter("notification.created", "type", NotificationType.SYSTEM.name()).increment();
		log.info("SYSTEM notification {} ({}) delivered to {} users", saved.getId(), saved.getDedupeKey(), recipients);
		return new SystemNotificationResultDTO(saved.getId(), recipients);
	}

	private static NotificationPageDTO.Item toItem(UserNotification row) {
		Notification n = row.getNotification();
		return new NotificationPageDTO.Item(row.getId(), n.getType(), n.getTitle(), n.getContent(), n.getRoute(),
				row.getIsRead(), row.getReadAt(), n.getCreatedAt());
	}

	/** DATETIME(6) precision, so a second A3 returns exactly the readAt the first one did. */
	static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
