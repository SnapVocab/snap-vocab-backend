package vn.ptit.snapvocab.progress.service;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.progress.domain.LearningEvent;
import vn.ptit.snapvocab.progress.domain.dto.ForecastDTO;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.progress.repository.LearningEventRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

/**
 * Adaptive-learning stats from data the app already keeps: FSRS state per card (forecast) and the review log in
 * {@code learning_events} (best study time). Nothing is stored.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

	static final int FORECAST_DAYS = 7;
	/** Slots of 4 hours: 0-4, 4-8, ..., 20-24 local time. */
	static final int SLOT_HOURS = 4;
	/** A follow-up review sooner than this is a learning step, not a test of memory. */
	static final Duration MIN_GAP = Duration.ofHours(12);
	static final int MIN_PAIRS = 30;
	static final int MIN_SLOT_REVIEWS = 5;
	static final int HISTORY_DAYS = 90;
	private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final ZoneOffset FSRS_ZONE = ZoneOffset.UTC;
	private static final List<CardState> STARTED = List.of(CardState.LEARNING, CardState.REVIEW, CardState.RELEARNING);

	private final StudyQueueRepository studyQueueRepository;
	private final LearningEventRepository learningEventRepository;
	private final Scheduler scheduler = Scheduler.builder().build();

	@Transactional(readOnly = true)
	public ForecastDTO forecast(Long userId) {
		Instant now = Instant.now();
		List<FsrsRecord> records = studyQueueRepository.findForForecast(userId, STARTED);
		List<Card> cards = records.stream().filter(r -> r.getLastReview() != null && r.getStability() != null
				&& r.getStability() > 0).map(ForecastService::toCard).toList();

		LocalDate today = LocalDate.ofInstant(now, STUDY_ZONE);
		List<ForecastDTO.Day> days = new ArrayList<>();
		for (int d = 0; d < FORECAST_DAYS; d++) {
			LocalDate date = today.plusDays(d);
			Instant start = date.atStartOfDay(STUDY_ZONE).toInstant();
			Instant end = date.plusDays(1).atStartOfDay(STUDY_ZONE).toInstant();
			boolean first = d == 0;
			int due = (int) records.stream()
					.map(r -> r.getDue().toInstant(FSRS_ZONE))
					.filter(at -> at.isBefore(end) && (first || !at.isBefore(start)))
					.count();
			days.add(new ForecastDTO.Day(date, due, averageRetention(cards, end)));
		}
		return new ForecastDTO(days, averageRetention(cards, now), records.size(), bestTime(userId, now));
	}

	ForecastDTO.BestTime bestTime(Long userId, Instant now) {
		List<LearningEvent> events = learningEventRepository
				.findByUser_IdAndEventTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(userId,
						LearningEventType.SRS_REVIEW_COMPLETED, now.minus(Duration.ofDays(HISTORY_DAYS)));
		return bestTime(events);
	}

	/** Pairs each review with the same card's next review at least {@link #MIN_GAP} later. */
	static ForecastDTO.BestTime bestTime(List<LearningEvent> events) {
		int slotCount = 24 / SLOT_HOURS;
		int[] reviews = new int[slotCount];
		int[] recalled = new int[slotCount];
		Map<Long, LearningEvent> previousByItem = new HashMap<>();
		int pairs = 0;
		for (LearningEvent event : events) {
			Long itemId = itemId(event);
			if (itemId == null) {
				continue;
			}
			LearningEvent previous = previousByItem.put(itemId, event);
			if (previous == null || Duration.between(previous.getCreatedAt(), event.getCreatedAt()).compareTo(MIN_GAP) < 0) {
				continue;
			}
			int slot = previous.getCreatedAt().atZone(STUDY_ZONE).getHour() / SLOT_HOURS;
			reviews[slot]++;
			if (!"AGAIN".equals(rating(event))) {
				recalled[slot]++;
			}
			pairs++;
		}
		List<ForecastDTO.Slot> slots = new ArrayList<>();
		Integer best = null;
		double bestRate = -1;
		for (int i = 0; i < slotCount; i++) {
			Double rate = reviews[i] >= MIN_SLOT_REVIEWS ? round((double) recalled[i] / reviews[i]) : null;
			slots.add(new ForecastDTO.Slot(i * SLOT_HOURS, reviews[i], rate));
			if (rate != null && (rate > bestRate || (best != null && rate == bestRate && reviews[i] > reviews[best / SLOT_HOURS]))) {
				bestRate = rate;
				best = i * SLOT_HOURS;
			}
		}
		boolean enough = pairs >= MIN_PAIRS && best != null;
		return new ForecastDTO.BestTime(enough, pairs, MIN_PAIRS, enough ? best : null, slots);
	}

	private Double averageRetention(List<Card> cards, Instant at) {
		if (cards.isEmpty()) {
			return null;
		}
		double sum = 0;
		for (Card card : cards) {
			sum += scheduler.getCardRetrievability(card, at);
		}
		return round(sum / cards.size());
	}

	private static Card toCard(FsrsRecord record) {
		Card.Builder builder = Card.builder()
				.cardId(Math.floorMod(record.getTopicItem().getId().hashCode(), Integer.MAX_VALUE))
				.state(switch (record.getState()) {
					case REVIEW -> State.REVIEW;
					case RELEARNING -> State.RELEARNING;
					default -> State.LEARNING;
				})
				.step(record.getStep())
				.stability(record.getStability().doubleValue())
				.difficulty(record.getDifficulty().doubleValue())
				.due(record.getDue().toInstant(FSRS_ZONE));
		LocalDateTime lastReview = record.getLastReview();
		builder.lastReview(lastReview.toInstant(FSRS_ZONE));
		return builder.build();
	}

	private static Long itemId(LearningEvent event) {
		Object value = event.getMetadata() == null ? null : event.getMetadata().get("topicItemId");
		return value instanceof Number number ? number.longValue() : null;
	}

	private static String rating(LearningEvent event) {
		Object value = event.getMetadata() == null ? null : event.getMetadata().get("rating");
		return value == null ? null : value.toString();
	}

	private static double round(double value) {
		return Math.round(value * 1000) / 1000.0;
	}
}
