package vn.ptit.snapvocab.adminstats.service;

import static vn.ptit.snapvocab.adminstats.service.AdminOverviewService.byDay;
import static vn.ptit.snapvocab.adminstats.service.AdminOverviewService.n;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Cards;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Cohort;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Economy;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Funnel;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.HardWord;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.ItemSales;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Latency;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Learning;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.LearningDay;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.MissionCoin;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Reliability;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Retention;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Scan;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.Segment;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.SegmentId;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.TopicStat;
import vn.ptit.snapvocab.adminstats.dto.AdminAnalyticsDTO.UnmappedLabel;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.CoinDay;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ErrorCount;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanDay;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CodeCount;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CoinDayRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CohortActive;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CohortSize;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.DayCount;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.FunnelRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.HardRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.ItemLapses;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.Percentiles;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.QuizDayRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.ScanDayRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.SegmentRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.StateCount;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.StreakRow;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver.ItemContent;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

/**
 * Analytics numbers per window, computed per call (admin_analytics_backend.md §6). Raw counts only: reading
 * thresholds live in the client (OQ-ANL-01). Retention ignores {@code days}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnalyticsService {

	static final Set<Integer> DAYS = Set.of(7, 30, 90);
	static final int HARD_WORDS = 20;
	static final int COHORT_WEEKS = 8;
	static final int STREAK_DAYS = 30;

	private final AdminStatsRepository stats;
	private final TopicRepository topicRepository;
	private final QuizContentResolver quizContentResolver;
	private final WordRepository wordRepository;
	private final ScanProperties scanProperties;
	private final Clock clock;

	public AdminAnalyticsDTO analytics(int days) {
		if (!DAYS.contains(days)) {
			throw new BadRequestAlertException("days must be 7, 30 or 90");
		}
		Instant now = clock.instant();
		StatsWindow w = StatsWindow.at(now, ProgressService.ZONE);
		Instant start = w.start(w.from(days));
		Instant end = w.end(w.today());
		List<String> dates = w.days(days);
		return new AdminAnalyticsDTO(now, days, learning(w, dates, start, end, now), retention(w, now),
				economy(dates, w.start(w.from(2 * days)), start, end), scan(dates, start, end));
	}

	private Learning learning(StatsWindow w, List<String> dates, Instant start, Instant end, Instant now) {
		Map<String, Long> reviews = byDay(stats.reviewsPerDay(w.from(dates.size()), w.today()), DayCount::getDay,
				DayCount::getCount);
		Map<String, Long> newWords = byDay(stats.newWordsPerDay(start, end), DayCount::getDay, DayCount::getCount);
		Map<String, QuizDayRow> quiz = byDay(stats.quizPerDay(start, end), QuizDayRow::getDay, Function.identity());
		List<LearningDay> daily = dates.stream().map(d -> {
			QuizDayRow q = quiz.get(d);
			return new LearningDay(d, reviews.getOrDefault(d, 0L), newWords.getOrDefault(d, 0L),
					q == null ? 0 : n(q.getAnswers()), q == null ? 0 : n(q.getCorrect()));
		}).toList();
		List<TopicStat> topics = stats.topicAccuracy(start, end).stream()
				.map(r -> new TopicStat(n(r.getCollectionId()), n(r.getRootId()), r.getRootName(), n(r.getTopicId()),
						r.getName(), n(r.getAnswers()), n(r.getCorrect()), n(r.getLearners())))
				.toList();
		return new Learning(
				cards(stats.cardStates(LocalDateTime.ofInstant(now, ZoneOffset.UTC)), n(stats.countOwnWordsNeverReviewed())),
				daily, topics, hardWords(stats.hardItems(start, end)));
	}

	/** §6: own words never reviewed count as new; review splits at the mastered stability. */
	static Cards cards(List<StateCount> rows, long neverReviewed) {
		long[] total = new long[5];
		long mastered = 0;
		long due = 0;
		for (StateCount r : rows) {
			if (r.getState() != null && r.getState() >= 0 && r.getState() < total.length) {
				total[r.getState()] = n(r.getTotal());
			}
			mastered += n(r.getMastered());
			due += n(r.getDue());
		}
		return new Cards(total[0] + neverReviewed, total[1], total[3], total[2] - mastered, mastered, total[4], due);
	}

	/** Rows in A-HARD order; items whose quiz word cannot be read are skipped (§3.1). */
	private List<HardWord> hardWords(List<HardRow> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<Long, Topic> topics = topicRepository.findAllById(rows.stream().map(HardRow::getTopicId).distinct().toList())
				.stream().collect(Collectors.toMap(Topic::getId, Function.identity()));
		Map<Long, Long> lapses = stats.lapses(rows.stream().map(HardRow::getItemId).toList()).stream()
				.collect(Collectors.toMap(ItemLapses::getItemId, r -> n(r.getLapses())));
		List<HardWord> words = new ArrayList<>();
		for (HardRow r : rows) {
			Topic topic = topics.get(r.getTopicId());
			String word = topic == null ? null
					: quizContentResolver.resolveQuizReady(topic, r.getItemId()).map(ItemContent::targetWord).orElse(null);
			if (word == null) {
				continue;
			}
			words.add(new HardWord(word, n(r.getCollectionId()), n(r.getRootId()), n(r.getTopicId()), r.getTopicName(),
					n(r.getAnswers()), n(r.getWrong()), n(lapses.get(r.getItemId())), n(r.getLearners())));
			if (words.size() == HARD_WORDS) {
				break;
			}
		}
		return words;
	}

	private Retention retention(StatsWindow w, Instant now) {
		LocalDate firstMonday = firstMonday(w.today());
		Instant firstMondayStart = w.start(firstMonday);
		SegmentRow s = stats.segments(w.start(w.today().minusDays(1)), w.start(w.today()),
				now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(7)));
		return new Retention(n(stats.countActivated()),
				cohorts(firstMonday, stats.cohortSizes(firstMonday, firstMondayStart),
						stats.cohortActivity(firstMonday, firstMondayStart)),
				longestReached(stats.longestStreaks()),
				List.of(new Segment(SegmentId.STREAK_AT_RISK, n(s.getStreakAtRisk())),
						new Segment(SegmentId.IDLE_8_30, n(s.getIdle())),
						new Segment(SegmentId.NEVER_STUDIED, n(s.getNeverStudied())),
						new Segment(SegmentId.PENDING_EMAIL, n(s.getPendingEmail()))));
	}

	/** Monday of the oldest of the 8 sign-up weeks; the last one is the current, unfinished week. */
	static LocalDate firstMonday(LocalDate today) {
		return today.with(DayOfWeek.MONDAY).minusWeeks(COHORT_WEEKS - 1L);
	}

	/** Cohort k has 8 − k weeks of activity; week 0 is its sign-up week. Out-of-range rows are dropped. */
	static List<Cohort> cohorts(LocalDate firstMonday, List<CohortSize> sizes, List<CohortActive> activity) {
		long[] size = new long[COHORT_WEEKS];
		long[][] active = new long[COHORT_WEEKS][COHORT_WEEKS];
		for (CohortSize r : sizes) {
			if (r.getCohort() != null && r.getCohort() >= 0 && r.getCohort() < COHORT_WEEKS) {
				size[r.getCohort()] = n(r.getCount());
			}
		}
		for (CohortActive r : activity) {
			int k = r.getCohort() == null ? -1 : r.getCohort();
			int week = r.getWeek() == null ? -1 : r.getWeek() - k;
			if (k >= 0 && k < COHORT_WEEKS && week >= 0 && week < COHORT_WEEKS - k) {
				active[k][week] = n(r.getLearners());
			}
		}
		List<Cohort> cohorts = new ArrayList<>();
		for (int k = 0; k < COHORT_WEEKS; k++) {
			cohorts.add(new Cohort(firstMonday.plusWeeks(k).toString(), size[k],
					Arrays.stream(active[k], 0, COHORT_WEEKS - k).boxed().toList()));
		}
		return cohorts;
	}

	/** reached[d − 1] = learners with longest streak ≥ d; rows are already capped at 30. */
	static List<Long> longestReached(List<StreakRow> rows) {
		long[] reached = new long[STREAK_DAYS];
		for (StreakRow r : rows) {
			int d = Math.min(r.getDays() == null ? 0 : r.getDays(), STREAK_DAYS);
			for (int i = 0; i < d; i++) {
				reached[i] += n(r.getCount());
			}
		}
		return Arrays.stream(reached).boxed().toList();
	}

	private Economy economy(List<String> dates, Instant prevStart, Instant start, Instant end) {
		Map<String, CoinDayRow> coins = byDay(stats.coinPerDay(start, end), CoinDayRow::getDay, Function.identity());
		List<CoinDay> daily = dates.stream().map(d -> {
			CoinDayRow r = coins.get(d);
			return r == null ? new CoinDay(d, 0, 0) : new CoinDay(d, n(r.getCoinIn()), n(r.getCoinOut()));
		}).toList();
		return new Economy(daily,
				stats.coinInByMission(start, end).stream().map(r -> new MissionCoin(r.getCode(), n(r.getCoin()))).toList(),
				n(stats.adjustments(start, end)),
				stats.sales(prevStart, start, end).stream()
						.map(r -> new ItemSales(r.getCode(), n(r.getPurchases()), n(r.getCoin()), n(r.getPrevPurchases())))
						.toList(),
				n(stats.circulation()));
	}

	private Scan scan(List<String> dates, Instant start, Instant end) {
		Map<String, ScanDayRow> scans = byDay(stats.scanPerDay(start, end), ScanDayRow::getDay, Function.identity());
		List<ScanDay> daily = dates.stream().map(d -> {
			ScanDayRow r = scans.get(d);
			return r == null ? new ScanDay(d, 0, 0) : new ScanDay(d, n(r.getDone()), n(r.getFailed()));
		}).toList();
		FunnelRow f = stats.funnel(start, end);
		// KT-A7: "not measured yet" rather than a 0% step until a saved word carries its scan.
		Long saved = n(stats.savedSourceLive()) > 0 ? n(f.getSaved()) : null;
		Map<String, Long> reliability = stats.labelsByReliability(start, end).stream()
				.collect(Collectors.toMap(CodeCount::getCode, CodeCount::getCount));
		Percentiles latency = stats.scanLatency(start, end);
		return new Scan(AdminOverviewService.scanConfig(scanProperties), stats.latestModel(start, end), daily,
				new Funnel(n(f.getSubmitted()), n(f.getDone()), n(f.getWithLabels()), n(f.getWithWord()), saved),
				new Reliability(n(reliability.get("HIGH")), n(reliability.get("MEDIUM")), n(reliability.get("LOW"))),
				new Latency(latency.getP50(), latency.getP95(), latency.getP99()),
				stats.scanErrors(start, end).stream()
						.map(r -> new ErrorCount(ScanErrorCode.valueOf(r.getCode()), n(r.getCount()))).toList(),
				n(stats.quotaHitLearners(start, end)),
				AdminOverviewService.stillUnmapped(stats.unmappedLabels(start, end), wordRepository).stream()
						.map(r -> new UnmappedLabel(r.getLabel(), n(r.getScans()), n(r.getLearners()), r.getLastSeen()))
						.toList());
	}
}
