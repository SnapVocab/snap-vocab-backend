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
import org.springframework.transaction.annotation.Isolation;
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
import vn.ptit.snapvocab.study.domain.CollectionStudySettings;
import vn.ptit.snapvocab.study.domain.dto.LibraryStudySettingsDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyCollectionDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyQueueDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyTodayDTO;
import vn.ptit.snapvocab.study.exception.StudyQuotaExceededException;
import vn.ptit.snapvocab.study.repository.StudyDailyCardRepository;
import vn.ptit.snapvocab.study.repository.StudyDailyProgressRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
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
	/** Transport batch size only. Daily budgets belong to each collection. */
	static final int STUDY_QUEUE_BATCH_SIZE = 20;
	private static final ZoneOffset FSRS_ZONE = ZoneOffset.UTC;
	// The app currently targets Vietnam. Keep study-day calculation independent from the server zone.
	private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final List<CardState> REVIEWABLE_STATES = List.of(
			CardState.NEW, CardState.LEARNING, CardState.REVIEW, CardState.RELEARNING);

	private final StudyQueueRepository studyQueueRepository;
	private final StudyDailyProgressRepository dailyProgressRepository;
	private final StudyDailyCardRepository dailyCardRepository;
    private final CollectionStudyPlanner collectionPlanner;
    private final vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository collectionSettingsRepository;
	private final TopicRepository topicRepository;
	private final TopicItemRepository topicItemRepository;
	private final CollectionRepository collectionRepository;
	private final UserRepository userRepository;
	private final TopicService topicService;
	private final XpService xpService;
	private final MissionTracker missionTracker;
	private final ApplicationEventPublisher eventPublisher;
	private final Scheduler scheduler = Scheduler.builder().build();

	@Transactional
	public StudyQueueDTO getQueue(Long userId, Long rootTopicId) {
        Topic topic = getAccessibleTopic(userId, rootTopicId);
        return buildQueue(userId, Map.of(topic.getCollection().getId(),
                topicRepository.findDescendantIdsIncludingSelf(rootTopicId)));
	}

	@Transactional
	public StudyQueueDTO getCollectionQueue(Long userId, Long collectionId) {
		getAccessibleCollection(userId, collectionId);
        return buildQueue(userId, Map.of(collectionId, topicRepository.findIdsByCollectionId(collectionId)));
	}

	/** The Learn tab's session: every collection in the learner's library (learn_ui.md UI-LEARN-03). */
	@Transactional
	public StudyQueueDTO getTodayQueue(Long userId) {
        Map<Long, List<Long>> scopes = new LinkedHashMap<>();
        for (Collection collection : activeLibraryCollections(userId)) {
            scopes.put(collection.getId(), topicRepository.findIdsByCollectionId(collection.getId()));
        }
        return buildQueue(userId, scopes);
	}

	@Transactional(readOnly = true)
    public StudyTodayDTO getToday(Long userId) {
        List<Collection> collections = activeLibraryCollections(userId);
        List<Long> collectionIds = collections.stream().map(Collection::getId).toList();
        var snapshot = collectionPlanner.snapshot(userId, collectionIds, Instant.now());
        long due = 0;
        int fresh = 0;
        int newPerDay = 0;
        long words = 0;
        for (Long id : collectionIds) {
            var totals = snapshot.collections().getOrDefault(id, CollectionStudyPlanner.Totals.EMPTY);
            var budget = snapshot.budgets().get(id);
            var counts = budget.counts(id, null, totals);
            due += counts.getDueCount();
            fresh = Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) fresh + counts.newCount()));
            newPerDay = Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) newPerDay + budget.newLimit()));
            words += totals.words();
        }
        int limit = STUDY_QUEUE_BATCH_SIZE;
        List<Long> topicIds = collectionIds.isEmpty() ? List.of() : topicRepository.findIdsByCollectionIdIn(collectionIds);
        LocalDateTime endOfTomorrow = LocalDate.now(STUDY_ZONE).plusDays(2).atStartOfDay(STUDY_ZONE)
                .withZoneSameInstant(FSRS_ZONE).toLocalDateTime();
        long tomorrow = topicIds.isEmpty() ? 0 : studyQueueRepository.countDue(userId, topicIds, endOfTomorrow, REVIEWABLE_STATES);
        return new StudyTodayDTO(due, fresh, (int) Math.min(limit, due + fresh),
                (int) dailyCardRepository.countByUserIdAndStudyDate(userId, LocalDate.now(STUDY_ZONE)),
                newPerDay, false, false, tomorrow, words);
    }

	/** "Bộ từ của bạn": most due first, then most recently studied (learn_ui.md §8.2 D). */
	@Transactional(readOnly = true)
	public List<StudyCollectionDTO> getLibrary(Long userId) {
		Map<Long, StudyQueueRepository.StudiedCollection> studied = studyQueueRepository
				.findStudiedCollections(userId, nowUtc(), REVIEWABLE_STATES).stream()
				.collect(Collectors.toMap(StudyQueueRepository.StudiedCollection::getCollectionId, Function.identity()));
		List<Collection> collections = activeLibraryCollections(userId);
		if (collections.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> wordCounts = topicItemRepository
				.countByCollectionIds(collections.stream().map(Collection::getId).toList()).stream()
				.collect(Collectors.toMap(TopicItemRepository.CollectionWordCount::getCollectionId,
						TopicItemRepository.CollectionWordCount::getTotal));
        var snapshot = collectionPlanner.snapshot(userId, collections.stream().map(Collection::getId).toList(), Instant.now());
		return collections.stream()
				.map(collection -> {
					StudyQueueRepository.StudiedCollection stats = studied.get(collection.getId());
					return new StudyCollectionDTO(collection.getId(), collection.getName(), collection.getTranslation(),
							collection.getType(), wordCounts.getOrDefault(collection.getId(), 0L),
							snapshot.budgets().get(collection.getId()).counts(collection.getId(), null,
                                    snapshot.collections().getOrDefault(collection.getId(), CollectionStudyPlanner.Totals.EMPTY)).getDueCount(),
                            snapshot.budgets().get(collection.getId()).counts(collection.getId(), null,
                                    snapshot.collections().getOrDefault(collection.getId(), CollectionStudyPlanner.Totals.EMPTY)).newCount(),
							stats == null ? null : stats.getLastReview());
				})
				.sorted(Comparator.comparingLong(StudyCollectionDTO::dueCount).reversed()
						.thenComparing(StudyCollectionDTO::lastStudiedAt, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(StudyCollectionDTO::id, Comparator.reverseOrder()))
				.toList();
	}

    private StudyQueueDTO buildQueue(Long userId, Map<Long, List<Long>> scopes) {
        int limit = STUDY_QUEUE_BATCH_SIZE;
        Instant instant = Instant.now();
        LocalDateTime now = LocalDateTime.ofInstant(instant, FSRS_ZONE);
        LocalDateTime dayStart = LocalDate.now(STUDY_ZONE).atStartOfDay(STUDY_ZONE)
                .withZoneSameInstant(FSRS_ZONE).toLocalDateTime();
        var snapshot = collectionPlanner.snapshot(userId, new ArrayList<>(scopes.keySet()), instant);
        List<FsrsRecord> dueRecords = new ArrayList<>();
        List<TopicItem> newItems = new ArrayList<>();
        for (var scope : scopes.entrySet()) {
            if (scope.getValue().isEmpty()) continue;
            var budget = snapshot.budgets().get(scope.getKey());
            dueRecords.addAll(studyQueueRepository.findIntradayLearning(userId, scope.getValue(), now,
                    dayStart, CollectionStudyPlanner.LEARNING_STATES, PageRequest.of(0, limit)));
            if (budget.reviewRemaining() > 0) {
                dueRecords.addAll(studyQueueRepository.findQuotaReviews(userId, scope.getValue(), now, dayStart,
                        REVIEWABLE_STATES, CollectionStudyPlanner.LEARNING_STATES,
                        PageRequest.of(0, Math.min(limit, budget.reviewRemaining()))));
            }
            newItems.addAll(findNewItems(userId, scope.getValue(), Math.min(limit, budget.newRemaining())));
        }
        dueRecords = dueRecords.stream().sorted(Comparator
                .comparingInt((FsrsRecord r) -> intradayLearning(r, dayStart) ? 0 : 1)
                .thenComparing(FsrsRecord::getDue).thenComparing(r -> r.getTopicItem().getId()))
                .limit(limit).toList();
        newItems = newItems.stream().sorted(Comparator.comparing(TopicItem::getId))
                .limit(Math.max(0, limit - dueRecords.size())).toList();
        List<Long> itemIds = new ArrayList<>();
        dueRecords.forEach(record -> itemIds.add(record.getTopicItem().getId()));
        newItems.forEach(item -> itemIds.add(item.getId()));
        if (itemIds.isEmpty()) return new StudyQueueDTO(limit, List.of());
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
        return new StudyQueueDTO(limit, cards);
    }

    private static boolean intradayLearning(FsrsRecord record, LocalDateTime dayStart) {
        return record != null && CollectionStudyPlanner.LEARNING_STATES.contains(record.getState())
                && record.getLastReview() != null && !record.getLastReview().isBefore(dayStart);
    }

    /**
     * Settings → "Giới hạn học theo bộ": the library's collections with their daily limits in one call, so the screen
     * does not open the collection list and make the learner find each collection's ⋮ menu (UX audit #12).
     */
    @Transactional(readOnly = true)
    public List<LibraryStudySettingsDTO> getLibrarySettings(Long userId) {
        List<Collection> collections = activeLibraryCollections(userId);
        if (collections.isEmpty()) {
            return List.of();
        }
        Map<Long, CollectionStudySettings> custom = collectionSettingsRepository
                .findByUserIdAndCollectionIdIn(userId, collections.stream().map(Collection::getId).toList()).stream()
                .collect(Collectors.toMap(s -> s.getCollection().getId(), Function.identity()));
        return collections.stream()
                .map(collection -> {
                    CollectionStudySettings settings = custom.get(collection.getId());
                    return new LibraryStudySettingsDTO(collection.getId(), collection.getName(), collection.getTranslation(),
                            collection.getType(),
                            settings == null ? CollectionStudySettingsService.DEFAULT_NEW_PER_DAY : settings.getNewCardsPerDay(),
                            settings == null ? CollectionStudySettingsService.DEFAULT_REVIEW_PER_DAY : settings.getReviewCardsPerDay(),
                            settings != null);
                })
                // Own collections first, then system ones; each by name.
                .sorted(Comparator.comparing((LibraryStudySettingsDTO row) -> row.type() != CollectionType.USER)
                        .thenComparing(LibraryStudySettingsDTO::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<Collection> activeLibraryCollections(Long userId) {
        Set<Long> ids = studyQueueRepository.findStudiedCollections(userId, nowUtc(), REVIEWABLE_STATES)
                .stream().map(StudyQueueRepository.StudiedCollection::getCollectionId)
                .collect(Collectors.toCollection(java.util.HashSet::new));
        ids.addAll(collectionSettingsRepository.findCollectionIdsByUserId(userId));
        return libraryCollections(userId, ids);
    }

	/** Own collections plus system collections already studied or personally configured. */
	private List<Collection> libraryCollections(Long userId, Set<Long> studiedIds) {
		Map<Long, Collection> byId = new LinkedHashMap<>();
		collectionRepository.findByOwnerId(userId).forEach(collection -> byId.put(collection.getId(), collection));
		collectionRepository.findAllById(studiedIds).stream()
				.filter(collection -> collection.getType() == CollectionType.SYSTEM)
				.forEach(collection -> byId.putIfAbsent(collection.getId(), collection));
		return new ArrayList<>(byId.values());
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

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ReviewResultDTO submitRating(Long userId, Long rootTopicId, Long topicItemId, Rating rating) {
		Topic root = getAccessibleTopic(userId, rootTopicId);
		List<Long> topicIds = topicRepository.findDescendantIdsIncludingSelf(root.getId());
		TopicItem item = getItem(topicItemId);
		if (!topicIds.contains(item.getTopic().getId())) {
			throw new AccessDeniedException("Topic item is outside the selected study topic");
		}
		return review(userId, root.getCollection(), item, rating);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
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
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ReviewResultDTO submitTodayRating(Long userId, Long topicItemId, Rating rating) {
		TopicItem item = getItem(topicItemId);
		Collection collection = item.getTopic().getCollection();
		ensureAccessible(userId, collection);
		return review(userId, collection, item, rating);
	}

	private ReviewResultDTO review(Long userId, Collection collection, TopicItem item, Rating rating) {
		// READ_COMMITTED ensures the quota query sees the previous writer after waiting for this lock,
		// even when collection access was checked before the lock (MariaDB defaults to REPEATABLE_READ).
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
        var budget = collectionPlanner.snapshot(userId, List.of(collection.getId()), reviewedAt)
                .budgets().get(collection.getId());
        LocalDateTime dayStart = today.atStartOfDay(STUDY_ZONE).withZoneSameInstant(FSRS_ZONE).toLocalDateTime();
        if (record == null && budget.newRemaining() == 0) {
            throw new StudyQuotaExceededException();
        }
        if (record != null && !alreadyCounted && !intradayLearning(record, dayStart) && budget.reviewRemaining() == 0) {
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
		if (!alreadyCounted) {
			// STUDY_N_CARDS counts distinct cards per study day, like review XP (daily_mission.md §5.1).
			missionTracker.apply(userId, today, MissionTriggerEvent.SRS_REVIEW_COMPLETED,
					"SRS_CARD_DAY:" + userId + ":" + item.getId() + ":" + today, MissionFacts.NONE, reviewedAt);
		}
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
