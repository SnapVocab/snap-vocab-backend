package vn.ptit.snapvocab.notification.service;

import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.notification.domain.Notification;
import vn.ptit.snapvocab.notification.domain.UserNotification;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;
import vn.ptit.snapvocab.notification.repository.DeviceTokenRepository;
import vn.ptit.snapvocab.notification.repository.NotificationRepository;
import vn.ptit.snapvocab.notification.repository.NotificationRepository.SrsReminderCandidate;
import vn.ptit.snapvocab.notification.repository.NotificationRepository.StreakWarningCandidate;
import vn.ptit.snapvocab.notification.repository.UserNotificationRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;
import vn.ptit.snapvocab.vocab.repository.FsrsRecordRepository;

/**
 * Background jobs of notification.md §4.4: J1 daily review reminder, J3 retention, J4 streak warning. No ShedLock:
 * several instances are safe because the UNIQUE dedupe_key lets only one of them insert, and only the inserter pushes
 * (§5.1).
 */
@Slf4j
@Component
public class NotificationJobs {

	/** §2.3: SRS_REMINDER always opens today's review. */
	static final String SRS_ROUTE = "/study/select";
	static final int WINDOW_MINUTES = 30;
	static final int RETENTION_DAYS = 90;
	/** UI-NOTIF-05: reminders are only worth their day; everything else (SYSTEM) keeps {@link #RETENTION_DAYS}. */
	static final int REMINDER_RETENTION_DAYS = 7;
	static final List<String> REMINDER_TYPES = List.of(NotificationType.SRS_REMINDER.name(),
			NotificationType.STREAK_WARNING.name());
	static final List<String> ALL_TYPES = Arrays.stream(NotificationType.values()).map(Enum::name).toList();
	/** OQ-NOTIF-06: a chain shorter than this is not worth a warning. */
	static final int STREAK_WARNING_MIN = 3;
	static final int TOKEN_IDLE_DAYS = 60;
	static final int DELETE_BATCH = 5000;

	private final NotificationRepository notificationRepository;
	private final UserNotificationRepository userNotificationRepository;
	private final DeviceTokenRepository deviceTokenRepository;
	private final FsrsRecordRepository fsrsRecordRepository;
	private final UserRepository userRepository;
	private final StreakFreezeService streakFreezeService;
	private final PushSender pushSender;
	private final TransactionTemplate transaction;
	private final boolean srsReminderEnabled;
	private final boolean streakWarningEnabled;

	public NotificationJobs(NotificationRepository notificationRepository,
			UserNotificationRepository userNotificationRepository, DeviceTokenRepository deviceTokenRepository,
			FsrsRecordRepository fsrsRecordRepository, UserRepository userRepository,
			StreakFreezeService streakFreezeService, PushSender pushSender, PlatformTransactionManager transactionManager,
			// Off until the app ships push and SRS (DEP-NOTIF-01/02); turn on per environment.
			@Value("${notification.srs-reminder.enabled:false}") boolean srsReminderEnabled,
			// Off until the app that renders STREAK_WARNING ships; turn on per environment.
			@Value("${notification.streak-warning.enabled:false}") boolean streakWarningEnabled) {
		this.notificationRepository = notificationRepository;
		this.userNotificationRepository = userNotificationRepository;
		this.deviceTokenRepository = deviceTokenRepository;
		this.fsrsRecordRepository = fsrsRecordRepository;
		this.userRepository = userRepository;
		this.streakFreezeService = streakFreezeService;
		this.pushSender = pushSender;
		this.transaction = new TransactionTemplate(transactionManager);
		this.srsReminderEnabled = srsReminderEnabled;
		this.streakWarningEnabled = streakWarningEnabled;
	}

	@Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
	public void srsReminderTick() {
		if (srsReminderEnabled) {
			runSrsReminders(Instant.now());
		}
	}

	/** 21:00 VN, then every 5 minutes until 21:55 so one missed tick (restart, deploy) is caught up. */
	@Scheduled(cron = "0 */5 21 * * *", zone = "Asia/Ho_Chi_Minh")
	public void streakWarningTick() {
		if (streakWarningEnabled) {
			runStreakWarnings(Instant.now());
		}
	}

	@Scheduled(cron = "0 0 3 * * SUN", zone = "Asia/Ho_Chi_Minh")
	public void retentionTick() {
		purge(Instant.now());
	}

	/**
	 * J1: learners whose reminder time (VN time of day) fell in the last 30 minutes of today, with at least one due
	 * card and no reminder yet today. Returns how many reminders were created. One learner failing never stops the rest.
	 */
	public int runSrsReminders(Instant now) {
		LocalDateTime nowVn = LocalDateTime.ofInstant(now, ProgressService.ZONE);
		LocalTime end = nowVn.toLocalTime().truncatedTo(ChronoUnit.SECONDS);
		LocalTime start = end.minusMinutes(WINDOW_MINUTES);
		if (start.isAfter(end)) {
			start = LocalTime.MIDNIGHT; // the window never crosses midnight (§5.5)
		}
		LocalDate today = nowVn.toLocalDate();
		LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC); // fsrs_records.due is UTC (TD-NOTIF-01)
		int created = 0;
		for (SrsReminderCandidate c : notificationRepository.findSrsReminderCandidates(start, end, today)) {
			try {
				long due = fsrsRecordRepository.countByUserIdAndStateNotAndDueLessThanEqual(c.getUserId(),
						CardState.SUSPENDED, nowUtc);
				if (due == 0) {
					continue; // B3: nothing written; a later scan in the window counts again
				}
				SrsReminderTemplate.Rendered copy = SrsReminderTemplate.render(c.getUiLanguage(), c.getFirstName(), due);
				UserNotification saved = createReminder(c.getUserId(), NotificationType.SRS_REMINDER, copy,
						Notification.srsReminderDedupeKey(c.getUserId(), today), now);
				if (saved == null) {
					continue;
				}
				created++;
				if (c.isPushEnabled()) {
					push(c.getUserId(), saved);
				}
			} catch (RuntimeException e) {
				log.error("SRS reminder failed for user {}", c.getUserId(), e);
			}
		}
		return created;
	}

	/**
	 * J4 (OQ-NOTIF-06): learners whose streak of at least 3 days ends tonight because they have not studied today. With
	 * a Streak Freeze unit left the chain survives, so the copy says a unit will be used instead. Returns how many
	 * warnings were created; one learner failing never stops the rest.
	 */
	public int runStreakWarnings(Instant now) {
		LocalDate today = LocalDate.ofInstant(now, ProgressService.ZONE);
		int created = 0;
		for (StreakWarningCandidate c : notificationRepository.findStreakWarningCandidates(STREAK_WARNING_MIN, today)) {
			try {
				User user = userRepository.findById(c.getUserId()).orElse(null);
				if (user == null || user.getLastStudiedAt() == null
						|| !LocalDate.ofInstant(user.getLastStudiedAt(), ProgressService.ZONE).isBefore(today)) {
					continue; // studied today: nothing to lose
				}
				StreakFreezeService.Settlement s = streakFreezeService.preview(user, today);
				int streak = s.streak(user.getStreakDays(), today);
				if (streak < STREAK_WARNING_MIN) {
					continue; // already broken, settled lazily
				}
				SrsReminderTemplate.Rendered copy = StreakWarningTemplate.render(c.getUiLanguage(), streak,
						s.remaining() > 0);
				UserNotification saved = createReminder(c.getUserId(), NotificationType.STREAK_WARNING, copy,
						Notification.streakWarningDedupeKey(c.getUserId(), today), now);
				if (saved == null) {
					continue;
				}
				created++;
				if (!Boolean.FALSE.equals(c.getPushEnabled())) {
					push(c.getUserId(), saved);
				}
			} catch (RuntimeException e) {
				log.error("Streak warning failed for user {}", c.getUserId(), e);
			}
		}
		return created;
	}

	/**
	 * B4: own transaction; null when another instance (or a previous scan) already created today's copy. The new
	 * reminder supersedes older unread ones of its type (UI-NOTIF-05), rolled back with it on a duplicate.
	 */
	private UserNotification createReminder(Long userId, NotificationType type, SrsReminderTemplate.Rendered copy,
			String dedupeKey, Instant now) {
		try {
			UserNotification saved = transaction.execute(s -> {
				Notification n = notificationRepository.saveAndFlush(Notification.builder()
						.type(type)
						.title(copy.title())
						.content(copy.body())
						.route(SRS_ROUTE)
						.dedupeKey(dedupeKey)
						.build());
				userNotificationRepository.markUnreadOfTypeRead(userId, type, now);
				return userNotificationRepository.save(UserNotification.builder()
						.user(userRepository.getReferenceById(userId))
						.notification(n)
						.build());
			});
			Metrics.counter("notification.created", "type", type.name()).increment();
			return saved;
		} catch (DataIntegrityViolationException e) {
			Metrics.counter("notification.deduped", "type", type.name()).increment();
			return null;
		}
	}

	/**
	 * B5: after commit, to every device of the learner; a push failure leaves the in-app copy as it is. {@code badge}
	 * sets the iOS app icon number to the unread count (OQ-NOTIF-06).
	 */
	private void push(Long userId, UserNotification saved) {
		Notification n = saved.getNotification();
		Map<String, Object> data = Map.of("userNotificationId", saved.getId(), "userId", userId,
				"type", n.getType().name(), "route", n.getRoute());
		int unread = (int) userNotificationRepository.countByUserIdAndIsReadFalse(userId);
		List<PushSender.PushMessage> messages = new ArrayList<>();
		for (String token : deviceTokenRepository.findTokensByUserId(userId)) {
			messages.add(new PushSender.PushMessage(token, n.getTitle(), n.getContent(), data, unread));
		}
		pushSender.send(messages);
	}

	/**
	 * J3: reminders older than 7 days (UI-NOTIF-05) and every other notification older than 90 days (OQ-NOTIF-05),
	 * with their inbox rows, in batches of 5000 so no transaction holds the tables for long; then device tokens unseen
	 * for 60 days.
	 */
	public void purge(Instant now) {
		long inbox = 0;
		long messages = 0;
		for (Map.Entry<List<String>, Integer> rule : Map.of(REMINDER_TYPES, REMINDER_RETENTION_DAYS, ALL_TYPES,
				RETENTION_DAYS).entrySet()) {
			Instant cutoff = now.minus(rule.getValue(), ChronoUnit.DAYS);
			inbox += drain(() -> userNotificationRepository.deleteOfNotificationsCreatedBefore(rule.getKey(), cutoff,
					DELETE_BATCH));
			messages += drain(() -> notificationRepository.deleteCreatedBefore(rule.getKey(), cutoff, DELETE_BATCH));
		}
		Integer tokens = transaction.execute(s -> deviceTokenRepository.deleteNotSeenSince(now.minus(TOKEN_IDLE_DAYS,
				ChronoUnit.DAYS)));
		if (tokens != null && tokens > 0) {
			Metrics.counter("device_token.removed", "reason", "expired").increment(tokens);
		}
		log.info("Notification retention: {} inbox rows, {} notifications, {} device tokens removed", inbox, messages, tokens);
	}

	private long drain(IntSupplier batch) {
		long total = 0;
		int deleted;
		do {
			deleted = transaction.execute(s -> batch.getAsInt());
			total += deleted;
		} while (deleted == DELETE_BATCH);
		return total;
	}
}
