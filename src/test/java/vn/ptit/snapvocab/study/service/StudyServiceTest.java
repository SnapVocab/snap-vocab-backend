package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.openspacedrepetition.Rating;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.study.domain.StudyDailyProgress;
import vn.ptit.snapvocab.study.domain.dto.SrsReviewCompletedEvent;
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
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.vocab.service.TopicService;
import vn.ptit.snapvocab.xp.domain.ExperienceLog;
import vn.ptit.snapvocab.xp.service.XpService;

@ExtendWith(MockitoExtension.class)
class StudyServiceTest {

	// 17:30 UTC is already the following study day in Vietnam.
	private static final Instant REVIEWED_AT = Instant.parse("2026-10-31T17:30:00Z");
	private static final LocalDate STUDY_DAY = LocalDate.of(2026, 11, 1);
	@Mock private StudyQueueRepository studyQueueRepository;
	@Mock private StudyDailyProgressRepository dailyProgressRepository;
	@Mock private StudyDailyCardRepository dailyCardRepository;
	@Mock private TopicRepository topicRepository;
	@Mock private TopicItemRepository topicItemRepository;
	@Mock private CollectionRepository collectionRepository;
	@Mock private UserRepository userRepository;
    @Mock private CollectionStudyPlanner collectionPlanner;
    @Mock private vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository collectionSettingsRepository;
	@Mock private TopicService topicService;
	@Mock private MissionTracker missionTracker;
	@Mock private ApplicationEventPublisher eventPublisher;
	@Mock private XpService xpService;
	@InjectMocks private StudyService service;
	private Collection collection;
	private TopicItem item;

	@BeforeEach
	void setUp() {
		User user = new User();
		user.setId(7L);
		collection = new Collection();
		collection.setId(3L);
		collection.setOwner(user);
		Topic topic = new Topic();
		topic.setId(11L);
		topic.setCollection(collection);
		item = new TopicItem();
		item.setId(21L);
		item.setTopic(topic);
		when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(topicItemRepository.findById(21L)).thenReturn(Optional.of(item));
		when(dailyProgressRepository.findForUpdate(7L, 3L, STUDY_DAY))
				.thenReturn(Optional.of(new StudyDailyProgress()));
        when(collectionPlanner.snapshot(any(), any(), any())).thenReturn(new CollectionStudyPlanner.Snapshot(
                java.util.Map.of(3L, new CollectionStudyPlanner.Budget(20, 200, 0, 0)), java.util.Map.of(), java.util.Map.of()));
		lenient().when(xpService.award(anyLong(), any(), anyInt(), anyString()))
				.thenReturn(ExperienceLog.builder().amount(2).build());
	}

	@ParameterizedTest
	@EnumSource(Rating.class)
	void everyAcceptedRatingEmitsOneStudyActionAfterSaving(Rating rating) {
		when(collectionRepository.findById(3L)).thenReturn(Optional.of(collection));
		try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
			clock.when(Economy::now).thenReturn(REVIEWED_AT);
			service.submitCollectionRating(7L, 3L, 21L, rating);
		}
		ArgumentCaptor<SrsReviewCompletedEvent> event = ArgumentCaptor.forClass(SrsReviewCompletedEvent.class);
		var order = inOrder(userRepository, missionTracker, studyQueueRepository, eventPublisher);
		order.verify(userRepository).findByIdForUpdate(7L);
		order.verify(missionTracker).ensureAssigned(7L, STUDY_DAY, REVIEWED_AT);
		order.verify(studyQueueRepository).save(any(FsrsRecord.class));
		order.verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().reviewedAt()).isEqualTo(REVIEWED_AT);
		assertThat(event.getValue().rating()).isEqualTo(rating);
		assertThat(event.getValue().sourceKey()).isEqualTo("SRS_REVIEW_COMPLETED:7:21:1");
		// Missions count the card once per study day, not every rating (daily_mission.md §5.1).
		verify(missionTracker).apply(7L, STUDY_DAY, MissionTriggerEvent.SRS_REVIEW_COMPLETED,
				"SRS_CARD_DAY:7:21:" + STUDY_DAY, MissionFacts.NONE, REVIEWED_AT);
	}

	@Test
	void topicReviewAlsoRecordsAStudyDay() {
		when(topicRepository.findById(11L)).thenReturn(Optional.of(item.getTopic()));
		when(topicRepository.findDescendantIdsIncludingSelf(11L)).thenReturn(List.of(11L));
		try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
			clock.when(Economy::now).thenReturn(REVIEWED_AT);
			service.submitRating(7L, 11L, 21L, Rating.GOOD);
		}
		verify(eventPublisher).publishEvent(any(SrsReviewCompletedEvent.class));
	}

	@Test
	void repeatedRatingsOnOneCardHaveDifferentActionKeys() {
		when(collectionRepository.findById(3L)).thenReturn(Optional.of(collection));
		try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
			clock.when(Economy::now).thenReturn(REVIEWED_AT);
			service.submitCollectionRating(7L, 3L, 21L, Rating.AGAIN);
			ArgumentCaptor<FsrsRecord> saved = ArgumentCaptor.forClass(FsrsRecord.class);
			verify(studyQueueRepository).save(saved.capture());
			when(studyQueueRepository.findForUpdate(7L, 21L)).thenReturn(Optional.of(saved.getValue()));
			clock.when(Economy::now).thenReturn(REVIEWED_AT.plusSeconds(120));
			service.submitCollectionRating(7L, 3L, 21L, Rating.GOOD);
		}
		ArgumentCaptor<SrsReviewCompletedEvent> events = ArgumentCaptor.forClass(SrsReviewCompletedEvent.class);
		verify(eventPublisher, Mockito.times(2)).publishEvent(events.capture());
		assertThat(events.getAllValues()).extracting(SrsReviewCompletedEvent::sourceKey)
				.containsExactly("SRS_REVIEW_COMPLETED:7:21:1", "SRS_REVIEW_COMPLETED:7:21:2");
	}

	@Test
	void rejectedReviewDoesNotRecordStudyActivity() {
		when(collectionRepository.findById(3L)).thenReturn(Optional.of(collection));
		// A new card when the collection's new-word quota is used up.
        when(collectionPlanner.snapshot(any(), any(), any())).thenReturn(new CollectionStudyPlanner.Snapshot(
                java.util.Map.of(3L, new CollectionStudyPlanner.Budget(20, 200, 20, 0)), java.util.Map.of(), java.util.Map.of()));
		try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
			clock.when(Economy::now).thenReturn(REVIEWED_AT);
			assertThatThrownBy(() -> service.submitCollectionRating(7L, 3L, 21L, Rating.GOOD))
					.isInstanceOf(StudyQuotaExceededException.class);
		}
		verify(eventPublisher, never()).publishEvent(any(SrsReviewCompletedEvent.class));
		verify(studyQueueRepository, never()).save(any());
	}

    @Test
    void staleQueueCannotRateAnUnseenReviewAfterCollectionReviewLimitIsReached() {
        when(collectionRepository.findById(3L)).thenReturn(Optional.of(collection));
        when(collectionPlanner.snapshot(any(), any(), any())).thenReturn(new CollectionStudyPlanner.Snapshot(
                java.util.Map.of(3L, new CollectionStudyPlanner.Budget(20, 200, 0, 200)), java.util.Map.of(), java.util.Map.of()));
        var record = FsrsRecord.builder().topicItem(item).state(vn.ptit.snapvocab.vocab.domain.enumeration.CardState.REVIEW)
                .stability(5F).difficulty(5F).due(java.time.LocalDateTime.ofInstant(REVIEWED_AT.minusSeconds(86400), java.time.ZoneOffset.UTC))
                .lastReview(java.time.LocalDateTime.ofInstant(REVIEWED_AT.minusSeconds(86400), java.time.ZoneOffset.UTC)).build();
        when(studyQueueRepository.findForUpdate(7L, 21L)).thenReturn(Optional.of(record));
        try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
            clock.when(Economy::now).thenReturn(REVIEWED_AT);
            assertThatThrownBy(() -> service.submitCollectionRating(7L, 3L, 21L, Rating.GOOD))
                    .isInstanceOf(StudyQuotaExceededException.class);
        }
        verify(studyQueueRepository, never()).save(any());
    }

    @Test
    void intradayLearningContinuesEvenWhenBothDailyLimitsAreUsedUp() {
        when(collectionRepository.findById(3L)).thenReturn(Optional.of(collection));
        when(collectionPlanner.snapshot(any(), any(), any())).thenReturn(new CollectionStudyPlanner.Snapshot(
                java.util.Map.of(3L, new CollectionStudyPlanner.Budget(20, 200, 20, 200)), java.util.Map.of(), java.util.Map.of()));
        var record = FsrsRecord.builder().topicItem(item).state(vn.ptit.snapvocab.vocab.domain.enumeration.CardState.LEARNING)
                .stability(1F).difficulty(5F).step(0)
                .due(java.time.LocalDateTime.ofInstant(REVIEWED_AT.minusSeconds(60), java.time.ZoneOffset.UTC))
                .lastReview(java.time.LocalDateTime.ofInstant(REVIEWED_AT.minusSeconds(120), java.time.ZoneOffset.UTC)).build();
        when(studyQueueRepository.findForUpdate(7L, 21L)).thenReturn(Optional.of(record));
        try (MockedStatic<Economy> clock = Mockito.mockStatic(Economy.class, Mockito.CALLS_REAL_METHODS)) {
            clock.when(Economy::now).thenReturn(REVIEWED_AT);
            service.submitCollectionRating(7L, 3L, 21L, Rating.GOOD);
        }
        verify(studyQueueRepository).save(any());
    }
}
