package vn.ptit.snapvocab.study.service;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.study.domain.StudyDailyCard;
import vn.ptit.snapvocab.study.domain.dto.ReviewResultDTO;
import vn.ptit.snapvocab.study.domain.dto.SrsReviewCompletedEvent;
import vn.ptit.snapvocab.study.domain.dto.StudyCardDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyCollectionDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyQueueDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyTodayDTO;
import vn.ptit.snapvocab.study.exception.StudyQuotaExceededException;
import vn.ptit.snapvocab.study.repository.StudyDailyCardRepository;
import vn.ptit.snapvocab.study.repository.StudyDailyProgressRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.service.UserSettingsService;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.vocab.service.TopicService;
import vn.ptit.snapvocab.xp.domain.XpSourceType;
import vn.ptit.snapvocab.xp.service.XpService;

@Service
@RequiredArgsConstructor
public class StudyService {

	/** specs.md M4 done criteria: review = 2 XP; an active XP Booster doubles it (shop.md S6). */
	static final int XP_PER_REVIEW = 2;
	/** learn_ui.md UI-LEARN-06: more cards due than this many sessions pauses new words. */
	public static final int BACKLOG_SESSIONS = 3;
	/** learn_ui.md UI-LEARN-10: no study for this many days makes the next session a gentle one. */
	static final int RETURNING_AFTER_DAYS = 3;
	private static final ZoneOffset FSRS_ZONE = ZoneOffset.UTC;
	// The app currently targets Vietnam. Keep study-day calculation independent from the server zone.
	private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final List<CardState> REVIEWABLE_STATES = List.of(
			CardState.NEW, CardState.LEARNING, CardState.REVIEW, CardState.RELEARNING);

	private final StudyQueueRepository studyQueueRepository;
	private final StudyDailyProgressRepository dailyProgressRepository;
	private final StudyDailyCardRepository dailyCardRepository;
	private final TopicRepository topicRepository;
	private final TopicItemRepository topicItemRepository;
	private final CollectionRepository collectionRepository;
	private final UserRepository userRepository;
	private final UserSettingsService userSettingsService;
	private final TopicService topicService;
	private final XpService xpService;
	private final MissionTracker missionTracker;
	private final ApplicationEventPublisher eventPublisher;
	private final Scheduler scheduler = Scheduler.builder().build();

	@Transactional
	public StudyQueueDTO getQueue(Long userId, Long rootTopicId) {
		getAccessibleTopic(userId, rootTopicId);
		return buildQueue(userId, topicRepository.findDescendantIdsIncludingSelf(rootTopicId));
	}

	@Transactional
	public StudyQueueDTO getCollectionQueue(Long userId, Long collectionId) {
		getAccessibleCollection(userId, collectionId);
		return buildQueue(userId, topicRepository.findIdsByCollectionId(collectionId));
	}

	/** The Learn tab's session: every collection in the learner's library (learn_ui.md UI-LEARN-03). */
	@Transactional
	public StudyQueueDTO getTodayQueue(Long userId) {
		return buildQueue(userId, libraryTopicIds(userId));
	}

	@Transactional
	public StudyTodayDTO getToday(Long userId) {
		List<Long> topicIds = libraryTopicIds(userId);
		if (topicIds.isEmpty()) {
			Plan plan = plan(userId, 0);
			return new StudyTodayDTO(0, 0, 0, plan.studiedToday(), plan.newPerDay(), false, false, 0, 0);
		}
		long due = studyQueueRepository.countDue(userId, topicIds, nowUtc(), REVIEWABLE_STATES);
		Plan plan = plan(userId, due);
		int duePart = (int) Math.min(due, plan.limit());
		int newPart = (int) Math.min(studyQueueRepository.countNewItems(userId, topicIds), plan.newSlots(duePart));
		LocalDateTime endOfTomorrow = LocalDate.now(STUDY_ZONE).plusDays(2).atStartOfDay(STUDY_ZONE)
				.withZoneSameInstant(FSRS_ZONE).toLocalDateTime();
		return new StudyTodayDTO(due, newPart, duePart + newPart, plan.studiedToday(), plan.newPerDay(),
				plan.backlog(), plan.returning(),
				studyQueueRepository.countDue(userId, topicIds, endOfTomorrow, REVIEWABLE_STATES),
				topicItemRepository.countByTopicIdIn(topicIds));
	}

	/** "Bộ từ của bạn": most due first, then most recently studied (learn_ui.md §8.2 D). */
	@Transactional(readOnly = true)
	public List<StudyCollectionDTO> getLibrary(Long userId) {
		Map<Long, StudyQueueRepository.StudiedCollection> studied = studyQueueRepository
				.findStudiedCollections(userId, nowUtc(), REVIEWABLE_STATES).stream()
				.collect(Collectors.toMap(StudyQueueRepository.StudiedCollection::getCollectionId, Function.identity()));
		List<Collection> collections = libraryCollections(userId, studied.keySet());
		if (collections.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> wordCounts = topicItemRepository
				.countByCollectionIds(collections.stream().map(Collection::getId).toList()).stream()
				.collect(Collectors.toMap(TopicItemRepository.CollectionWordCount::getCollectionId,
						TopicItemRepository.CollectionWordCount::getTotal));
		return collections.stream()
				.map(collection -> {
					StudyQueueRepository.StudiedCollection stats = studied.get(collection.getId());
					return new StudyCollectionDTO(collection.getId(), collection.getName(), collection.getTranslation(),
							collection.getType(), wordCounts.getOrDefault(collection.getId(), 0L),
							stats == null || stats.getDue() == null ? 0 : stats.getDue(),
							stats == null ? null : stats.getLastReview());
				})
				.sorted(Comparator.comparingLong(StudyCollectionDTO::dueCount).reversed()
						.thenComparing(StudyCollectionDTO::lastStudiedAt, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(StudyCollectionDTO::id, Comparator.reverseOrder()))
				.toList();
	}

	private StudyQueueDTO buildQueue(Long userId, List<Long> topicIds) {
		if (topicIds.isEmpty()) {
			return new StudyQueueDTO(userSettingsService.getStudySessionCardLimit(userId), List.of());
		}
		LocalDateTime now = nowUtc();
		Plan plan = plan(userId, studyQueueRepository.countDue(userId, topicIds, now, REVIEWABLE_STATES));
		List<FsrsRecord> dueRecords = studyQueueRepository.findDueRecords(userId, topicIds, now,
				REVIEWABLE_STATES, PageRequest.of(0, plan.limit()));
		List<TopicItem> newItems = findNewItems(userId, topicIds, plan.newSlots(dueRecords.size()));

		List<Long> itemIds = new ArrayList<>();
		dueRecords.forEach(record -> itemIds.add(record.getTopicItem().getId()));
		newItems.forEach(item -> itemIds.add(item.getId()));
		Map<Long, TopicItemData> dataById = topicService.getTopicItemsByIds(itemIds).stream()
				.collect(Collectors.toMap(TopicItemData::getTopicItemId, Function.identity()));
		List<StudyCardDTO> cards = new ArrayList<>();
		for (FsrsRecord record : dueRecords) {
			Topic topic = record.getTopicItem().getTopic();
			cards.add(new StudyCardDTO(dataById.get(record.getTopicItem().getId()), topic.getId(),
					topic.getCollection().getName(), record.getState(), record.getDue()));
		}
		for (TopicItem item : newItems) {
			cards.add(new StudyCardDTO(dataById.get(item.getId()), item.getTopic().getId(),
					item.getTopic().getCollection().getName(), CardState.NEW, null));
		}
		return new StudyQueueDTO(plan.limit(), cards);
	}

	/**
	 * Today's rules (learn_ui.md §4.1, UI-LEARN-02/06/10): due cards are never capped, only chunked into
	 * sessions; new words are limited per learner per day ({@code dailyGoal}); a backlog pauses them; the first
	 * session after a break is half size and, when anything is due, reviews only.
	 */
	private Plan plan(Long userId, long dueInScope) {
		int sessionLimit = userSettingsService.getStudySessionCardLimit(userId);
		int newPerDay = userSettingsService.get(userId).dailyGoal();
		LocalDate today = LocalDate.now(STUDY_ZONE);
		int studiedToday = (int) dailyCardRepository.countByUserIdAndStudyDate(userId, today);
		Instant lastStudiedAt = userRepository.findById(userId).map(User::getLastStudiedAt).orElse(null);
		boolean returning = studiedToday == 0 && lastStudiedAt != null
				&& !LocalDate.ofInstant(lastStudiedAt, STUDY_ZONE).isAfter(today.minusDays(RETURNING_AFTER_DAYS));
		boolean backlog = studyQueueRepository.countDueAll(userId, nowUtc(), REVIEWABLE_STATES)
				> (long) BACKLOG_SESSIONS * sessionLimit;
		int newRemaining = Math.max(0, newPerDay - newWordsStarted(userId, today));
		boolean newPaused = backlog || (returning && dueInScope > 0);
		return new Plan(returning ? Math.max(1, sessionLimit / 2) : sessionLimit, newPaused ? 0 : newRemaining,
				newPerDay, studiedToday, backlog, returning);
	}

	private record Plan(int limit, int newRemaining, int newPerDay, int studiedToday, boolean backlog,
			boolean returning) {
		int newSlots(int dueCards) {
			return Math.min(newRemaining, Math.max(0, limit - dueCards));
		}
	}

	private int newWordsStarted(Long userId, LocalDate day) {
		return (int) studyQueueRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId,
				day.atStartOfDay(STUDY_ZONE).toInstant(), day.plusDays(1).atStartOfDay(STUDY_ZONE).toInstant());
	}

	/** Own collections plus system collections with at least one reviewed card. */
	private List<Collection> libraryCollections(Long userId, Set<Long> studiedIds) {
		Map<Long, Collection> byId = new LinkedHashMap<>();
		collectionRepository.findByOwnerId(userId).forEach(collection -> byId.put(collection.getId(), collection));
		collectionRepository.findAllById(studiedIds).stream()
				.filter(collection -> collection.getType() == CollectionType.SYSTEM)
				.forEach(collection -> byId.putIfAbsent(collection.getId(), collection));
		return new ArrayList<>(byId.values());
	}

	private List<Long> libraryTopicIds(Long userId) {
		Set<Long> studiedIds = studyQueueRepository.findStudiedCollections(userId, nowUtc(), REVIEWABLE_STATES)
				.stream().map(StudyQueueRepository.StudiedCollection::getCollectionId).collect(Collectors.toSet());
		List<Long> collectionIds = libraryCollections(userId, studiedIds).stream().map(Collection::getId).toList();
		return collectionIds.isEmpty() ? List.of() : topicRepository.findIdsByCollectionIdIn(collectionIds);
	}

	private static LocalDateTime nowUtc() {
		return LocalDateTime.ofInstant(Instant.now(), FSRS_ZONE);
	}

	private List<TopicItem> findNewItems(Long userId, List<Long> topicIds, int limit) {
		if (limit < 1) {
			return List.of();
		}
		if (topicIds.isEmpty()) {
			return List.of();
		}
		return studyQueueRepository.findNewItems(userId, topicIds, PageRequest.of(0, limit));
	}

	@Transactional
	public ReviewResultDTO submitRating(Long userId, Long rootTopicId, Long topicItemId, Rating rating) {
		Topic root = getAccessibleTopic(userId, rootTopicId);
		List<Long> topicIds = topicRepository.findDescendantIdsIncludingSelf(root.getId());
		TopicItem item = getItem(topicItemId);
		if (!topicIds.contains(item.getTopic().getId())) {
			throw new AccessDeniedException("Topic item is outside the selected study topic");
		}
		return review(userId, root.getCollection(), item, rating);
	}

	@Transactional
	public ReviewResultDTO submitCollectionRating(Long userId, Long collectionId, Long topicItemId,
			Rating rating) {
		Collection collection = getAccessibleCollection(userId, collectionId);
		TopicItem item = getItem(topicItemId);
		if (!collection.getId().equals(item.getTopic().getCollection().getId())) {
			throw new AccessDeniedException("Topic item is outside the selected collection");
		}
		return review(userId, collection, item, rating);
	}

	/** Review from the Learn tab's session, where cards come from any collection in the library. */
	@Transactional
	public ReviewResultDTO submitTodayRating(Long userId, Long topicItemId, Rating rating) {
		TopicItem item = getItem(topicItemId);
		Collection collection = item.getTopic().getCollection();
		ensureAccessible(userId, collection);
		return review(userId, collection, item, rating);
	}

	private ReviewResultDTO review(Long userId, Collection collection, TopicItem item, Rating rating) {
		// Keep the same writer lock order as quizzes, progress and mission rewards.
		Economy.lockUser(userRepository, userId);
		Instant reviewedAt = Economy.now();
		LocalDate today = LocalDate.ofInstant(reviewedAt, STUDY_ZONE);
		missionTracker.ensureAssigned(userId, today, reviewedAt);
		dailyProgressRepository.createIfMissing(userId, collection.getId(), today);
		dailyProgressRepository.findForUpdate(userId, collection.getId(), today)
				.orElseThrow(() -> new IllegalStateException("Daily progress row was not created"));

		boolean alreadyCounted = dailyCardRepository.existsByUserIdAndCollectionIdAndStudyDateAndTopicItemId(
				userId, collection.getId(), today, item.getId());
		FsrsRecord record = studyQueueRepository.findForUpdate(userId, item.getId()).orElse(null);
		Card previousCard = record == null ? newCard(item.getId()) : toFsrsCard(record);
		// Counted under the user lock, so parallel first reviews in different collections cannot overshoot.
		if (record == null && newWordsStarted(userId, today) >= userSettingsService.get(userId).dailyGoal()) {
			throw new StudyQuotaExceededException();
		}
		boolean wasMastered = isMastered(record);
		Card nextCard = scheduler.reviewCard(previousCard, rating, reviewedAt).card();

		if (record == null) {
			User user = userRepository.getReferenceById(userId);
			record = FsrsRecord.builder().user(user).topicItem(item).reps(0).lapses(0).build();
		}
		int reps = record.getReps() == null ? 0 : record.getReps();
		int lapses = record.getLapses() == null ? 0 : record.getLapses();
		if (rating == Rating.AGAIN && record.getState() == CardState.REVIEW) {
			lapses++;
		}
		record.setState(toDomainState(nextCard.getState()));
		record.setDue(LocalDateTime.ofInstant(nextCard.getDue(), FSRS_ZONE));
		record.setStability(nextCard.getStability().floatValue());
		record.setDifficulty(nextCard.getDifficulty().floatValue());
		record.setStep(nextCard.getStep());
		record.setLastReview(LocalDateTime.ofInstant(reviewedAt, FSRS_ZONE));
		record.setReps(reps + 1);
		record.setLapses(lapses);
		studyQueueRepository.save(record);

		int xpAwarded = 0;
		if (!alreadyCounted) {
			StudyDailyCard dailyCard = new StudyDailyCard();
			dailyCard.setUser(userRepository.getReferenceById(userId));
			dailyCard.setCollection(collection);
			dailyCard.setStudyDate(today);
			dailyCard.setTopicItem(item);
			dailyCardRepository.save(dailyCard);
			// XP only for a card's first review of the study day; re-rating it that day earns none (PO 2026-10-18).
			xpAwarded = xpService.award(userId, XpSourceType.FLASHCARD_REVIEW, XP_PER_REVIEW,
					"FLASHCARD_REVIEW:" + userId + ":" + item.getId() + ":" + today).getAmount();
		}
		// Every accepted rating is one study action (master 2026-10-31): learning event via ProgressEventListener.
		SrsReviewCompletedEvent event = new SrsReviewCompletedEvent(userId, collection.getId(), item.getId(),
				record.getReps(), rating, reviewedAt);
		eventPublisher.publishEvent(event);
		missionTracker.apply(userId, today, MissionTriggerEvent.SRS_REVIEW_COMPLETED, event.sourceKey(),
				MissionFacts.NONE, reviewedAt);
		return new ReviewResultDTO(item.getId(), record.getState(), record.getDue(),
				record.getStability(), record.getDifficulty(), record.getReps(), record.getLapses(),
				xpAwarded, !wasMastered && isMastered(record));
	}

	private static boolean isMastered(FsrsRecord record) {
		return record != null && record.getState() == CardState.REVIEW
				&& record.getStability() >= ProgressService.MASTERED_STABILITY;
	}

	private TopicItem getItem(Long topicItemId) {
		return topicItemRepository.findById(topicItemId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic item not found: " + topicItemId));
	}

	private Card newCard(Long topicItemId) {
		return Card.builder().cardId(Math.floorMod(topicItemId.hashCode(), Integer.MAX_VALUE)).build();
	}

	private Card toFsrsCard(FsrsRecord record) {
		if (record.getState() == CardState.SUSPENDED) {
			throw new IllegalStateException("Suspended cards cannot be reviewed");
		}
		Card.Builder builder = Card.builder()
				.cardId(Math.floorMod(record.getTopicItem().getId().hashCode(), Integer.MAX_VALUE))
				.state(toFsrsState(record.getState()))
				.step(record.getStep())
				.stability(record.getStability().doubleValue())
				.difficulty(record.getDifficulty().doubleValue())
				.due(record.getDue().toInstant(FSRS_ZONE));
		if (record.getLastReview() != null) {
			builder.lastReview(record.getLastReview().toInstant(FSRS_ZONE));
		}
		return builder.build();
	}

	private State toFsrsState(CardState state) {
		return switch (state) {
			case LEARNING -> State.LEARNING;
			case REVIEW -> State.REVIEW;
			case RELEARNING -> State.RELEARNING;
			case NEW -> State.LEARNING;
			case SUSPENDED -> throw new IllegalStateException("Suspended cards cannot be reviewed");
		};
	}

	private CardState toDomainState(State state) {
		return switch (state) {
			case LEARNING -> CardState.LEARNING;
			case REVIEW -> CardState.REVIEW;
			case RELEARNING -> CardState.RELEARNING;
		};
	}

	private Topic getAccessibleTopic(Long userId, Long topicId) {
		Topic topic = topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));
		ensureAccessible(userId, topic.getCollection());
		return topic;
	}

	private Collection getAccessibleCollection(Long userId, Long collectionId) {
		Collection collection = collectionRepository.findById(collectionId)
				.orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));
		ensureAccessible(userId, collection);
		return collection;
	}

	private void ensureAccessible(Long userId, Collection collection) {
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		boolean isSystemCollection = collection.getType() == CollectionType.SYSTEM;
		boolean isOwner = collection.getOwner() != null && collection.getOwner().getId().equals(userId);
		if (!isSystemCollection && !isOwner && !isAdmin) {
			throw new AccessDeniedException("You do not have access to this collection");
		}
	}
}
