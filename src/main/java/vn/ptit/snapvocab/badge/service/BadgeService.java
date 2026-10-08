package vn.ptit.snapvocab.badge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.UserBadge;
import vn.ptit.snapvocab.badge.domain.UserBadgeWordCredit;
import vn.ptit.snapvocab.badge.domain.dto.BadgesDTO;
import vn.ptit.snapvocab.badge.domain.dto.SeenBadgesResultDTO;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeWordCreditRepository;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

/**
 * Badges (badges.md §6, §8). Badges are granted only by {@link #sync}; producers never grant, they only record word
 * credits ({@link #recordWordCredit}). No reward, no notification, never revoked.
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

	static final int MAX_SEEN_IDS = 50;

	private final UserRepository userRepository;
	private final QuizRepository quizRepository;
	private final BadgeRepository badgeRepository;
	private final UserBadgeRepository userBadgeRepository;
	private final UserBadgeWordCreditRepository wordCreditRepository;

	/** §8.1: read-only; a badge that is due but not synced yet shows {@code earned = false}. */
	@Transactional(readOnly = true)
	public BadgesDTO getBadges(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
		return view(metrics(user), userBadgeRepository.findByUserId(userId));
	}

	/**
	 * §6.1: grants every active badge whose threshold is reached and not yet earned, then returns the view. The users
	 * lock serializes it with quiz complete, add-item and a concurrent sync; UNIQUE(user_id, badge_id) is the last line.
	 * READ_COMMITTED so a quiz committed while we waited for the lock is counted.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public BadgesDTO sync(Long userId) {
		User user = Economy.lockUser(userRepository, userId);
		Map<BadgeMetric, Long> values = metrics(user);
		List<UserBadge> earned = new ArrayList<>(userBadgeRepository.findByUserId(userId));
		List<Long> earnedIds = earned.stream().map(ub -> ub.getBadge().getId()).toList();
		for (Badge badge : badgeRepository.findAllByOrderBySortOrderAscIdAsc()) {
			if (badge.getActive() && !earnedIds.contains(badge.getId())
					&& values.get(badge.getMetric()) >= badge.getThreshold()) {
				earned.add(userBadgeRepository.save(UserBadge.builder().user(user).badge(badge).build()));
			}
		}
		return view(values, earned);
	}

	/** §8.3: idempotent; ids that are not the learner's unseen earned badges are ignored. */
	@Transactional
	public SeenBadgesResultDTO markSeen(Long userId, List<Long> badgeIds) {
		if (badgeIds == null || badgeIds.isEmpty() || badgeIds.size() > MAX_SEEN_IDS
				|| badgeIds.stream().anyMatch(id -> id == null || id <= 0)) {
			throw new BadRequestAlertException("VALIDATION_ERROR");
		}
		userBadgeRepository.markSeen(userId, badgeIds, Economy.now());
		return new SeenBadgesResultDTO(userBadgeRepository.countByUserIdAndSeenAtIsNull(userId));
	}

	/**
	 * §6.2: called from add-item for a quiz-ready item in the learner's own USER collection, inside its transaction
	 * and after it locked {@code users}. Counts each normalized word once per account; grants nothing.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordWordCredit(Long userId, byte[] wordHash, Instant actionInstant) {
		if (!wordCreditRepository.existsByUserIdAndWordHash(userId, wordHash)) {
			wordCreditRepository.save(UserBadgeWordCredit.builder()
					.user(userRepository.getReferenceById(userId))
					.wordHash(wordHash)
					.createdAt(actionInstant)
					.build());
		}
	}

	private Map<BadgeMetric, Long> metrics(User user) {
		Map<BadgeMetric, Long> values = new EnumMap<>(BadgeMetric.class);
		values.put(BadgeMetric.LONGEST_STREAK_DAYS, (long) user.getLongestStreakDays());
		values.put(BadgeMetric.QUIZ_COMPLETED, quizRepository.countCompleted(user.getId()));
		values.put(BadgeMetric.PERFECT_QUIZ, quizRepository.countPerfect(user.getId()));
		values.put(BadgeMetric.DISTINCT_WORDS_ADDED, wordCreditRepository.countByUserId(user.getId()));
		return values;
	}

	/** Active badges plus inactive ones the learner earned (§5.4), by sort order. */
	private BadgesDTO view(Map<BadgeMetric, Long> values, List<UserBadge> earned) {
		Map<Long, UserBadge> byBadgeId = earned.stream()
				.collect(Collectors.toMap(ub -> ub.getBadge().getId(), Function.identity()));
		List<BadgesDTO.Item> items = badgeRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.filter(b -> b.getActive() || byBadgeId.containsKey(b.getId()))
				.map(b -> item(b, values.get(b.getMetric()), byBadgeId.get(b.getId())))
				.toList();
		int earnedCount = (int) items.stream().filter(BadgesDTO.Item::earned).count();
		int unseenCount = (int) items.stream().filter(i -> Boolean.FALSE.equals(i.seen())).count();
		return new BadgesDTO(earnedCount, items.size(), unseenCount, items);
	}

	private static BadgesDTO.Item item(Badge b, long currentValue, UserBadge earned) {
		return new BadgesDTO.Item(b.getId(), b.getCode(), b.getName(), b.getDescription(), b.getMetric(),
				b.getThreshold(), currentValue, b.getIconKey(), b.getSortOrder(), earned != null,
				earned == null ? null : earned.getCreatedAt(), earned == null ? null : earned.getSeenAt() != null);
	}
}
