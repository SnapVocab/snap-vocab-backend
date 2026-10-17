package vn.ptit.snapvocab.study.service;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.study.domain.dto.ReviewResultDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyCardDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyQueueDTO;
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
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.vocab.service.TopicService;

@Service
@RequiredArgsConstructor
public class StudyService {

	private static final ZoneOffset FSRS_ZONE = ZoneOffset.UTC;
	private static final List<CardState> REVIEWABLE_STATES = List.of(
			CardState.NEW, CardState.LEARNING, CardState.REVIEW, CardState.RELEARNING);

	private final StudyQueueRepository studyQueueRepository;
	private final TopicRepository topicRepository;
	private final TopicItemRepository topicItemRepository;
	private final UserRepository userRepository;
	private final UserSettingsService userSettingsService;
	private final TopicService topicService;
	private final Scheduler scheduler = Scheduler.builder().build();

	@Transactional
	public StudyQueueDTO getQueue(Long userId, Long rootTopicId) {
		Topic root = getAccessibleTopic(userId, rootTopicId);
		List<Long> topicIds = topicRepository.findDescendantIdsIncludingSelf(root.getId());
		int limit = userSettingsService.getStudySessionCardLimit(userId);
		LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), FSRS_ZONE);

		List<FsrsRecord> dueRecords = studyQueueRepository.findDueRecords(
				userId, topicIds, now, REVIEWABLE_STATES, PageRequest.of(0, limit));
		int remaining = limit - dueRecords.size();
		List<TopicItem> newItems = remaining == 0 ? List.of()
				: studyQueueRepository.findNewItems(userId, topicIds, PageRequest.of(0, remaining));

		List<Long> allItemIds = new ArrayList<>();
		dueRecords.forEach(record -> allItemIds.add(record.getTopicItem().getId()));
		newItems.forEach(item -> allItemIds.add(item.getId()));
		Map<Long, TopicItemData> dataById = topicService.getTopicItemsByIds(allItemIds).stream()
				.collect(Collectors.toMap(TopicItemData::getTopicItemId, Function.identity()));

		List<StudyCardDTO> cards = new ArrayList<>();
		for (FsrsRecord record : dueRecords) {
			cards.add(new StudyCardDTO(dataById.get(record.getTopicItem().getId()),
					record.getState(), record.getDue()));
		}
		for (TopicItem item : newItems) {
			cards.add(new StudyCardDTO(dataById.get(item.getId()), CardState.NEW, null));
		}
		return new StudyQueueDTO(limit, cards);
	}

	@Transactional
	public ReviewResultDTO submitRating(Long userId, Long rootTopicId, Long topicItemId, Rating rating) {
		Topic root = getAccessibleTopic(userId, rootTopicId);
		List<Long> topicIds = topicRepository.findDescendantIdsIncludingSelf(root.getId());
		TopicItem item = topicItemRepository.findById(topicItemId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic item not found: " + topicItemId));
		if (!topicIds.contains(item.getTopic().getId())) {
			throw new AccessDeniedException("Topic item is outside the selected study topic");
		}

		FsrsRecord record = studyQueueRepository.findForUpdate(userId, topicItemId).orElse(null);
		Card previousCard = record == null ? newCard(topicItemId) : toFsrsCard(record);
		Instant reviewedAt = Instant.now();
		Card nextCard = scheduler.reviewCard(previousCard, rating, reviewedAt).card();

		if (record == null) {
			User user = userRepository.getReferenceById(userId);
			record = FsrsRecord.builder().user(user).topicItem(item)
					.reps(0).lapses(0).build();
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

		return new ReviewResultDTO(topicItemId, record.getState(), record.getDue(),
				record.getStability(), record.getDifficulty(), record.getReps(), record.getLapses());
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
		Collection collection = topic.getCollection();
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		boolean isSystemCollection = collection.getType() == CollectionType.SYSTEM;
		boolean isOwner = collection.getOwner() != null
				&& collection.getOwner().getId().equals(userId);
		if (!isSystemCollection && !isOwner && !isAdmin) {
			throw new AccessDeniedException("You do not have access to this topic");
		}
		return topic;
	}
}
