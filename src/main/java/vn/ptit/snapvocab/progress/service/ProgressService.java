package vn.ptit.snapvocab.progress.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.domain.LearningEvent;
import vn.ptit.snapvocab.progress.domain.dto.HomeWidgetDTO;
import vn.ptit.snapvocab.progress.domain.dto.ProgressHistoryDTO;
import vn.ptit.snapvocab.progress.domain.dto.ProgressSummaryDTO;
import vn.ptit.snapvocab.progress.domain.dto.StreakDTO;
import vn.ptit.snapvocab.progress.domain.enumeration.HistoryPeriod;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.progress.repository.LearningEventRepository;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;
import vn.ptit.snapvocab.vocab.repository.FsrsRecordRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;

/**
 * SS-12 Progress. Learning-state counts come from {@code fsrs_records} (specs.md FR-04 map, mastered =
 * REVIEW and stability ≥ 21), streak from {@code users}, activity history from {@code learning_events}.
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

	public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	public static final float MASTERED_STABILITY = 21f;
	static final int MAX_HISTORY_DAYS = 366;

	private final UserRepository userRepository;
	private final LearningEventRepository learningEventRepository;
	private final FsrsRecordRepository fsrsRecordRepository;
	private final TopicItemRepository topicItemRepository;
	private final QuizRepository quizRepository;
	private final MissionTracker missionTracker;
	private final StreakFreezeService streakFreezeService;

	@Transactional(readOnly = true)
	public ProgressSummaryDTO getSummary(Long userId) {
		QuizRepository.CompletedTotals quiz = quizRepository.completedTotals(userId);
		Double accuracy = quiz.getTotalUnits() == 0 ? null : (double) quiz.getCorrectCount() / quiz.getTotalUnits();
		return new ProgressSummaryDTO(
				topicItemRepository.countOwnedBy(userId),
				learnedCount(userId),
				fsrsRecordRepository.countByUserIdAndStateAndStabilityLessThan(userId, CardState.REVIEW, MASTERED_STABILITY),
				masteredCount(userId),
				dueCount(userId),
				accuracy,
				learningEventRepository.countByUserIdAndEventType(userId, LearningEventType.SRS_REVIEW_COMPLETED),
				quiz.getQuizCount());
	}

	/** Read-only: a pending Streak Freeze settlement is shown, not written (streak_freeze.md §3.2). */
	@Transactional(readOnly = true)
	public StreakDTO getStreak(Long userId) {
		User user = findUser(userId);
		LocalDate today = today();
		StreakFreezeService.Settlement s = streakFreezeService.preview(user, today);
		return new StreakDTO(s.streak(user.getStreakDays(), today), user.getLongestStreakDays(), lastStudiedDate(user),
				s.remaining(), s.lastFrozen());
	}

	@Transactional(readOnly = true)
	public ProgressHistoryDTO getHistory(Long userId, HistoryPeriod period, LocalDate from, LocalDate to) {
		LocalDate end = to == null ? today() : to;
		LocalDate start = from == null ? defaultFrom(period, end) : from;
		if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_HISTORY_DAYS) {
			throw new BadRequestAlertException("from must be <= to and the range at most " + MAX_HISTORY_DAYS + " days");
		}
		Map<LocalDate, Long> perDay = new HashMap<>();
		learningEventRepository.countPerDay(userId, start, end)
				.forEach(d -> perDay.put(d.getLearningDate(), d.getEventCount()));
		return new ProgressHistoryDTO(period, start, end, buckets(period, start, end, perDay));
	}

	@Transactional(readOnly = true)
	public HomeWidgetDTO getHomeWidget(Long userId) {
		User user = findUser(userId);
		LocalDate today = today();
		return new HomeWidgetDTO(
				topicItemRepository.countOwnedBy(userId),
				learnedCount(userId),
				masteredCount(userId),
				dueCount(userId),
				streakFreezeService.preview(user, today).streak(user.getStreakDays(), today));
	}

	/** {@link #record(Long, LearningEventType, String, Map, Instant)} at the current server time. */
	@Transactional
	public void record(Long userId, LearningEventType type, String sourceKey, Map<String, Object> metadata) {
		record(userId, type, sourceKey, metadata, Economy.now());
	}

	/**
	 * Records one learning action in the caller's transaction and advances the streak.
	 * Idempotent by {@code sourceKey}: a replay records nothing. {@code actionInstant} is the producer's single
	 * timestamp (daily_mission.md §3.2), so the learning date and the Daily Mission credit fall on the same day.
	 * Does not assign missions: the producer already ran {@code ensureAssigned} before its own mutation.
	 */
	@Transactional
	public void record(Long userId, LearningEventType type, String sourceKey, Map<String, Object> metadata,
			Instant actionInstant) {
		User user = Economy.lockUser(userRepository, userId);
		if (learningEventRepository.existsBySourceKey(sourceKey)) {
			return;
		}
		LocalDate today = LocalDate.ofInstant(actionInstant, ZONE);
		LearningEvent event = learningEventRepository.save(LearningEvent.builder()
				.user(user)
				.eventType(type)
				.sourceKey(sourceKey)
				.learningDate(today)
				.metadata(metadata)
				.createdAt(actionInstant)
				.build());
		// Missed days take Streak Freeze units first; the chain then continues from the last covered day (§3.4).
		LocalDate covered = streakFreezeService.settle(user, today).coveredThrough();
		int streak = nextStreak(user.getStreakDays(), covered, today);
		user.setStreakDays(streak);
		user.setLongestStreakDays(Math.max(user.getLongestStreakDays(), streak));
		user.setLastStudiedAt(actionInstant);
		missionTracker.apply(userId, today, MissionTriggerEvent.LEARNING_ACTIVITY_RECORDED, "LEARNING:" + event.getId(),
				MissionFacts.NONE, actionInstant);
	}

	/**
	 * Streak after studying on {@code today}: unchanged same day, +1 the day after, else restart at 1.
	 * {@code covered} is the last day the chain reaches, a frozen day included (a frozen day adds nothing, PD-SF-01).
	 */
	static int nextStreak(int stored, LocalDate covered, LocalDate today) {
		if (today.equals(covered)) {
			return Math.max(stored, 1);
		}
		return today.minusDays(1).equals(covered) ? stored + 1 : 1;
	}

	static List<ProgressHistoryDTO.Bucket> buckets(HistoryPeriod period, LocalDate from, LocalDate to,
			Map<LocalDate, Long> perDay) {
		List<ProgressHistoryDTO.Bucket> result = new ArrayList<>();
		for (LocalDate start = align(period, from); !start.isAfter(to); start = next(period, start)) {
			long events = 0;
			int activeDays = 0;
			LocalDate end = next(period, start);
			for (LocalDate d = start.isBefore(from) ? from : start; d.isBefore(end) && !d.isAfter(to); d = d.plusDays(1)) {
				long count = perDay.getOrDefault(d, 0L);
				events += count;
				activeDays += count > 0 ? 1 : 0;
			}
			result.add(new ProgressHistoryDTO.Bucket(start, events, activeDays));
		}
		return result;
	}

	private static LocalDate align(HistoryPeriod period, LocalDate date) {
		return switch (period) {
			case DAY -> date;
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTH -> date.withDayOfMonth(1);
		};
	}

	private static LocalDate next(HistoryPeriod period, LocalDate start) {
		return switch (period) {
			case DAY -> start.plusDays(1);
			case WEEK -> start.plusWeeks(1);
			case MONTH -> start.plusMonths(1);
		};
	}

	/** Defaults: last 30 days, last 12 weeks, last 12 months — each within {@link #MAX_HISTORY_DAYS}. */
	private static LocalDate defaultFrom(HistoryPeriod period, LocalDate to) {
		return switch (period) {
			case DAY -> to.minusDays(29);
			case WEEK -> align(HistoryPeriod.WEEK, to).minusWeeks(11);
			case MONTH -> to.withDayOfMonth(1).minusMonths(11);
		};
	}

	private long learnedCount(Long userId) {
		return fsrsRecordRepository.countByUserIdAndStateNot(userId, CardState.NEW);
	}

	private long masteredCount(Long userId) {
		return fsrsRecordRepository.countByUserIdAndStateAndStabilityGreaterThanEqual(userId, CardState.REVIEW,
				MASTERED_STABILITY);
	}

	/** Workload, not a learning state; suspended cards are never due. {@code due} is stored in UTC (TD-NOTIF-01). */
	private long dueCount(Long userId) {
		return fsrsRecordRepository.countByUserIdAndStateNotAndDueLessThanEqual(userId, CardState.SUSPENDED,
				LocalDateTime.now(ZoneOffset.UTC));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
	}

	private static LocalDate lastStudiedDate(User user) {
		return user.getLastStudiedAt() == null ? null : LocalDate.ofInstant(user.getLastStudiedAt(), ZONE);
	}

	private static LocalDate today() {
		return LocalDate.now(ZONE);
	}
}
