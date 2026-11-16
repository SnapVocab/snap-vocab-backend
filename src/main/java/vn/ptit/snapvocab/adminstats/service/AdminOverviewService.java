package vn.ptit.snapvocab.adminstats.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.DayActive;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.Economy;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.ItemSales;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.Learners;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.MissionStat;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.Scan;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.ScanToday;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.Today;
import vn.ptit.snapvocab.adminstats.dto.AdminOverviewDTO.UnmappedLabel;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.CoinDay;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ErrorCount;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanConfig;
import vn.ptit.snapvocab.adminstats.dto.StatsDTOs.ScanDay;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.CoinDayRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.DayCount;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.Percentiles;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.ScanDayRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.ScanRefusals;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.TodayRow;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository.UnmappedRow;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.scan.config.ScanProperties;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;

/**
 * Dashboard numbers, computed per call from the business tables (admin_overview_backend.md §3, KT-S1). Raw counts
 * only: the "Cần xử lý" thresholds live in the client (OQ-DASH-01).
 *
 * <p>ponytail: no cache, ~13 queries per call; add {@code @Cacheable} 60 s if p95 passes §5's 1 s target.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOverviewService {

	static final int SERIES_DAYS = 14;
	static final int UNMAPPED_LIMIT = 20;

	private final AdminStatsRepository stats;
	private final WordRepository wordRepository;
	private final ScanProperties scanProperties;

	public AdminOverviewDTO overview() {
		StatsWindow w = StatsWindow.now(ProgressService.ZONE);
		Instant todayStart = w.start(w.today());
		Instant todayEnd = w.end(w.today());
		Instant start7 = w.start(w.from(7));
		List<String> days = w.days(SERIES_DAYS);

		Map<String, Long> active = byDay(stats.activePerDay(w.from(SERIES_DAYS), w.today()), DayCount::getDay,
				DayCount::getCount);
		List<DayActive> activeDaily = days.stream().map(d -> new DayActive(d, active.getOrDefault(d, 0L))).toList();
		TodayRow t = stats.today(w.today(), todayStart, todayEnd);
		Learners learners = new Learners(n(stats.countActivated()), n(stats.countActiveLearners(w.from(7), w.today())),
				activeDaily.get(activeDaily.size() - 1).active(), activeDaily,
				new Today(n(t.getNewWords()), n(t.getReviews()), n(t.getQuizzes()), n(t.getMissionsCompleted()),
						n(t.getFullSets())));

		Map<String, ScanDayRow> scans = byDay(stats.scanPerDay(w.start(w.from(SERIES_DAYS)), todayEnd),
				ScanDayRow::getDay, Function.identity());
		List<ScanDay> scanDaily = days.stream().map(d -> {
			ScanDayRow r = scans.get(d);
			return r == null ? new ScanDay(d, 0, 0) : new ScanDay(d, n(r.getDone()), n(r.getFailed()));
		}).toList();
		ScanDay scanToday = scanDaily.get(scanDaily.size() - 1);
		Percentiles latency = stats.scanLatency(todayStart, todayEnd);
		ScanRefusals refusals = stats.scanRefusals(todayStart, todayEnd);
		Scan scan = new Scan(scanConfig(scanProperties),
				new ScanToday(scanToday.done() + scanToday.failed(), scanToday.failed(), latency.getP50(),
						latency.getP95(), n(refusals.getQueueFull()), n(refusals.getQuotaHit())),
				scanDaily,
				stats.scanErrors(start7, todayEnd).stream()
						.map(r -> new ErrorCount(ScanErrorCode.valueOf(r.getCode()), n(r.getCount()))).toList());

		Map<String, CoinDayRow> coins = byDay(stats.coinPerDay(w.start(w.from(SERIES_DAYS)), todayEnd),
				CoinDayRow::getDay, Function.identity());
		Economy economy = new Economy(days.stream().map(d -> {
			CoinDayRow r = coins.get(d);
			return r == null ? new CoinDay(d, 0, 0) : new CoinDay(d, n(r.getCoinIn()), n(r.getCoinOut()));
		}).toList(), n(stats.avgBalance()));

		return new AdminOverviewDTO(Instant.now(), learners, scan, unmappedLabels(start7, todayEnd), economy,
				stats.missions(w.from(7), w.today()).stream()
						.map(r -> new MissionStat(r.getCode(), n(r.getAssigned()), n(r.getCompleted()))).toList(),
				stats.purchasesPerItem(w.start(w.from(30)), todayEnd).stream()
						.map(r -> new ItemSales(r.getCode(), n(r.getCount()))).toList());
	}

	private List<UnmappedLabel> unmappedLabels(Instant start, Instant end) {
		return stillUnmapped(stats.unmappedLabels(start, end), wordRepository).stream()
				.map(r -> new UnmappedLabel(r.getLabel(), n(r.getScans()), n(r.getLearners())))
				.toList();
	}

	/** Q-UNMAPPED rows still without a word in today's dictionary, top {@value #UNMAPPED_LIMIT} (KT-S5). */
	static List<UnmappedRow> stillUnmapped(List<UnmappedRow> rows, WordRepository wordRepository) {
		Set<String> words = new HashSet<>();
		rows.forEach(r -> {
			words.add(r.getLabel());
			if (r.getHeadword() != null) {
				words.add(r.getHeadword());
			}
		});
		Set<String> known = words.isEmpty() ? Set.of() : new HashSet<>(wordRepository.findExistingEnglish(words));
		return rows.stream()
				.filter(r -> !known.contains(r.getLabel().toLowerCase(Locale.ROOT))
						&& (r.getHeadword() == null || !known.contains(r.getHeadword().toLowerCase(Locale.ROOT))))
				.limit(UNMAPPED_LIMIT)
				.toList();
	}

	static ScanConfig scanConfig(ScanProperties scanProperties) {
		return new ScanConfig(scanProperties.getWorkers(), scanProperties.getQueueCapacity(),
				scanProperties.getQuotaPerDay(), scanProperties.getMinReliability().toUpperCase(Locale.ROOT));
	}

	static <R, V> Map<String, V> byDay(List<R> rows, Function<R, String> day, Function<R, V> value) {
		return rows.stream().collect(Collectors.toMap(day, value));
	}

	static long n(Long value) {
		return value == null ? 0 : value;
	}
}
