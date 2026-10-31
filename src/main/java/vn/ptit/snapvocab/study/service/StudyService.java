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
import java.util.HashSet;
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
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.study.domain.StudyDailyCard;
import vn.ptit.snapvocab.study.domain.dto.ReviewResultDTO;
import vn.ptit.snapvocab.study.domain.dto.SrsReviewCompletedEvent;
import vn.ptit.snapvocab.study.domain.dto.StudyCardDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyQueueDTO;
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

@Service
@RequiredArgsConstructor
public class StudyService {

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
	private final MissionTracker missionTracker;
	private final ApplicationEventPublisher eventPublisher;
	private final Scheduler scheduler = Scheduler.builder().build();

	@Transactional
	public StudyQueueDTO getQueue(Long userId, Long rootTopicId) {
		Topic root = getAccessibleTopic(userId, rootTopicId);
		List<Long> topicIds = topicRepository.findDescendantIdsIncludingSelf(root.getId());
		return buildQueue(userId, root.getCollection(), topicIds);
	}

	@Transactional
	public StudyQueueDTO getCollectionQueue(Long userId, Long collectionId) {
		Collection collection = getAccessibleCollection(userId, collectionId);
		List<Long> topicIds = topicRepository.findIdsByCollectionId(collectionId);
		return buildQueue(userId, collection, topicIds);
	}

	private StudyQueueDTO buildQueue(Long userId, Collection collection, List<Long> topicIds) {
		int sessionLimit = userSettingsService.getStudySessionCardLimit(userId);
		int dailyGoal = userSettingsService.get(userId).dailyGoal();
		LocalDate today = LocalDate.now(STUDY_ZONE);
		int studiedToday = (int) dailyCardRepository
				.countByUserIdAndCollectionIdAndStudyDate(userId, collection.getId(), today);
		int remainingToday = Math.max(0, dailyGoal - studiedToday);
		LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), FSRS_ZONE);

		List<FsrsRecord> dueRecords = topicIds.isEmpty() ? List.of()
				: studyQueueRepository.findDueRecords(userId, topicIds, now,
						REVIEWABLE_STATES, PageRequest.of(0, sessionLimit));
		Set<Long> studiedItemIds = new HashSet<>(dailyCardRepository
				.findTopicItemIdsStudiedToday(userId, collection.getId(), today));
		List<FsrsRecord> selectedDueRecords = new ArrayList<>();
		int uncountedDueCards = 0;
		for (FsrsRecord record : dueRecords) {
			Long itemId = record.getTopicItem().getId();
			if (studiedItemIds.contains(itemId) || uncountedDueCards < remainingToday) {
				selectedDueRecords.add(record);
				if (!studiedItemIds.contains(itemId)) {
					uncountedDueCards++;
				}
			}
		}
		int quotaRemainingAfterDue = Math.max(0, remainingToday - uncountedDueCards);
		int newLimit = Math.min(quotaRemainingAfterDue, Math.max(0, sessionLimit - selectedDueRecords.size()));
		List<TopicItem> newItems = findNewItems(userId, topicIds, newLimit);
		List<Long> itemIds = new ArrayList<>();
		for (FsrsRecord record : selectedDueRecords) {
			itemIds.add(record.getTopicItem().getId());
		}
		for (TopicItem item : newItems) {
			itemIds.add(item.getId());
		}

		Map<Long, TopicItemData> dataById = topicService.getTopicItemsByIds(itemIds).stream()
				.collect(Collectors.toMap(TopicItemData::getTopicItemId, Function.identity()));
		List<StudyCardDTO> cards = new ArrayList<>();
		for (FsrsRecord record : selectedDueRecords) {
			cards.add(new StudyCardDTO(dataById.get(record.getTopicItem().getId()),
				record.getTopicItem().getTopic().getId(), record.getState(), record.getDue()));
		}
		for (TopicItem item : newItems) {
			cards.add(new StudyCardDTO(dataById.get(item.getId()), item.getTopic().getId(), CardState.NEW, null));
		}
		return new StudyQueueDTO(sessionLimit, dailyGoal, studiedToday, remainingToday, cards);
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
		int dailyGoal = userSettingsService.get(userId).dailyGoal();
		long studiedBefore = dailyCardRepository.countByUserIdAndCollectionIdAndStudyDate(
				userId, collection.getId(), today);
		if (!alreadyCounted && studiedBefore >= dailyGoal) {
			throw new StudyQuotaExceededException();
		}

		FsrsRecord record = studyQueueRepository.findForUpdate(userId, item.getId()).orElse(null);
		Card previousCard = record == null ? newCard(item.getId()) : toFsrsCard(record);
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

		if (!alreadyCounted) {
			StudyDailyCard dailyCard = new StudyDailyCard();
			dailyCard.setUser(userRepository.getReferenceById(userId));
			dailyCard.setCollection(collection);
			dailyCard.setStudyDate(today);
			dailyCard.setTopicItem(item);
			dailyCardRepository.save(dailyCard);
		}
		int studiedToday = (int) dailyCardRepository
				.countByUserIdAndCollectionIdAndStudyDate(userId, collection.getId(), today);
		SrsReviewCompletedEvent event = new SrsReviewCompletedEvent(userId, collection.getId(), item.getId(),
				record.getReps(), rating, reviewedAt);
		eventPublisher.publishEvent(event);
		missionTracker.apply(userId, today, MissionTriggerEvent.SRS_REVIEW_COMPLETED, event.sourceKey(),
				MissionFacts.NONE, reviewedAt);
		return new ReviewResultDTO(item.getId(), record.getState(), record.getDue(),
				record.getStability(), record.getDifficulty(), record.getReps(), record.getLapses(),
				studiedToday, dailyGoal, Math.max(0, dailyGoal - studiedToday));
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
