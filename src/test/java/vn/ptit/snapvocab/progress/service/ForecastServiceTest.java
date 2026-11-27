package vn.ptit.snapvocab.progress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.progress.domain.LearningEvent;
import vn.ptit.snapvocab.progress.domain.dto.ForecastDTO;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.progress.repository.LearningEventRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	@Mock private StudyQueueRepository studyQueueRepository;
	@Mock private LearningEventRepository learningEventRepository;
	@InjectMocks private ForecastService service;

	private static LearningEvent review(long itemId, Instant at, String rating) {
		return LearningEvent.builder().eventType(LearningEventType.SRS_REVIEW_COMPLETED).createdAt(at)
				.metadata(Map.of("topicItemId", (int) itemId, "rating", rating)).build();
	}

	/** Local hour {@code hour} on day {@code day} of a fixed week. */
	private static Instant at(int day, int hour) {
		return LocalDateTime.of(2026, 11, 2 + day, hour, 0).atZone(VN).toInstant();
	}

	@Test
	void eachReviewIsJudgedByTheSameCardsNextReviewAtLeastTwelveHoursLater() {
		List<LearningEvent> events = List.of(
				review(1, at(0, 9), "GOOD"),
				// 10 minutes later: a learning step, not a test of memory, so the 9:00 review is not judged by it.
				review(1, at(0, 9).plus(Duration.ofMinutes(10)), "AGAIN"),
				review(1, at(1, 21), "GOOD"),   // judges the 9:10 review (slot 8-12): remembered
				review(1, at(3, 21), "AGAIN"),  // judges the day-1 21:00 review (slot 20-24): forgotten
				review(2, at(0, 22), "HARD"),
				review(2, at(2, 8), "EASY"));   // judges the 22:00 review (slot 20-24): remembered

		ForecastDTO.BestTime best = ForecastService.bestTime(events);

		assertThat(best.pairs()).isEqualTo(3);
		assertThat(best.slots()).extracting(ForecastDTO.Slot::reviews).containsExactly(0, 0, 1, 0, 0, 2);
		// Too few reviews per slot to rate, and too few pairs overall to name a best time.
		assertThat(best.slots()).extracting(ForecastDTO.Slot::recallRate).containsOnlyNulls();
		assertThat(best.enoughData()).isFalse();
		assertThat(best.bestStartHour()).isNull();
	}

	@Test
	void withEnoughPairsTheSlotWithTheBestNextDayRecallWins() {
		List<LearningEvent> events = new ArrayList<>();
		// Morning (8-12): 20 cards, 18 remembered the next day. Evening (20-24): 20 cards, 10 remembered.
		for (int i = 0; i < 20; i++) {
			events.add(review(100 + i, at(0, 9), "GOOD"));
			events.add(review(100 + i, at(1, 9), i < 18 ? "GOOD" : "AGAIN"));
			events.add(review(200 + i, at(0, 21), "GOOD"));
			events.add(review(200 + i, at(1, 21).plus(Duration.ofMinutes(30)), i < 10 ? "GOOD" : "AGAIN"));
		}
		events.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

		ForecastDTO.BestTime best = ForecastService.bestTime(events);

		assertThat(best.enoughData()).isTrue();
		assertThat(best.bestStartHour()).isEqualTo(8);
		assertThat(best.slots().get(2).recallRate()).isEqualTo(0.9);
		assertThat(best.slots().get(5).recallRate()).isEqualTo(0.5);
	}

	@Test
	void theForecastCountsDueCardsPerDayAndRetentionFallsWithoutReview() {
		TopicItem item = new TopicItem();
		item.setId(5L);
		Instant now = Instant.now();
		LocalDateTime lastReview = LocalDateTime.ofInstant(now.minus(Duration.ofDays(2)), ZoneOffset.UTC);
		FsrsRecord overdue = FsrsRecord.builder().topicItem(item).state(CardState.REVIEW).stability(3f).difficulty(5f)
				.lastReview(lastReview).due(LocalDateTime.ofInstant(now.minus(Duration.ofDays(1)), ZoneOffset.UTC)).build();
		FsrsRecord inThreeDays = FsrsRecord.builder().topicItem(item).state(CardState.REVIEW).stability(20f)
				.difficulty(5f).lastReview(lastReview)
				.due(LocalDateTime.ofInstant(now.plus(Duration.ofDays(3)), ZoneOffset.UTC)).build();
		when(studyQueueRepository.findForForecast(eq(7L), any())).thenReturn(List.of(overdue, inThreeDays));
		when(learningEventRepository.findByUser_IdAndEventTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
				eq(7L), eq(LearningEventType.SRS_REVIEW_COMPLETED), any())).thenReturn(List.of());

		ForecastDTO forecast = service.forecast(7L);

		assertThat(forecast.days()).hasSize(7);
		assertThat(forecast.cardsTracked()).isEqualTo(2);
		assertThat(forecast.days().get(0).dueCount()).isEqualTo(1);
		assertThat(forecast.days().stream().mapToInt(ForecastDTO.Day::dueCount).sum()).isEqualTo(2);
		assertThat(forecast.retentionNow()).isBetween(0.0, 1.0);
		assertThat(forecast.days().get(6).retention()).isLessThan(forecast.days().get(0).retention());
		assertThat(forecast.bestTime().enoughData()).isFalse();
	}

	@Test
	void aLearnerWithoutCardsGetsAnEmptyForecast() {
		when(studyQueueRepository.findForForecast(eq(7L), any())).thenReturn(List.of());
		when(learningEventRepository.findByUser_IdAndEventTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
				eq(7L), eq(LearningEventType.SRS_REVIEW_COMPLETED), any())).thenReturn(List.of());

		ForecastDTO forecast = service.forecast(7L);

		assertThat(forecast.retentionNow()).isNull();
		assertThat(forecast.days()).allSatisfy(day -> {
			assertThat(day.dueCount()).isZero();
			assertThat(day.retention()).isNull();
		});
	}
}
