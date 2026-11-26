package vn.ptit.snapvocab.progress.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.progress.domain.dto.ForecastDTO;
import vn.ptit.snapvocab.progress.domain.dto.HomeWidgetDTO;
import vn.ptit.snapvocab.progress.domain.dto.ProgressHistoryDTO;
import vn.ptit.snapvocab.progress.domain.dto.ProgressSummaryDTO;
import vn.ptit.snapvocab.progress.domain.dto.StreakDTO;
import vn.ptit.snapvocab.progress.domain.enumeration.HistoryPeriod;
import vn.ptit.snapvocab.progress.service.ForecastService;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.security.SecurityUtils;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@Tag(name = "Progress")
public class ProgressController {

	private final ProgressService progressService;
	private final ForecastService forecastService;

	@GetMapping("/summary")
	@Operation(summary = "Learning-state counts, due workload, accuracy and completed quizzes (MH-STATS-01)")
	public ResponseEntity<ProgressSummaryDTO> summary() {
		return ResponseEntity.ok(progressService.getSummary(currentUserId()));
	}

	@GetMapping("/streak")
	@Operation(summary = "Current and longest streak (MH-STATS-01)")
	public ResponseEntity<StreakDTO> streak() {
		return ResponseEntity.ok(progressService.getStreak(currentUserId()));
	}

	@GetMapping("/history")
	@Operation(summary = "Activity per day/week/month in Asia/Ho_Chi_Minh (MH-STATS-01)")
	public ResponseEntity<ProgressHistoryDTO> history(@RequestParam(defaultValue = "DAY") String period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ResponseEntity.ok(progressService.getHistory(currentUserId(), parsePeriod(period), from, to));
	}

	@GetMapping("/forecast")
	@Operation(summary = "7-day forgetting forecast and the time of day that sticks best")
	public ResponseEntity<ForecastDTO> forecast() {
		return ResponseEntity.ok(forecastService.forecast(currentUserId()));
	}

	@GetMapping("/home-widget")
	@Operation(summary = "Compact progress for the Home dashboard (MH-MAIN-01)")
	public ResponseEntity<HomeWidgetDTO> homeWidget() {
		return ResponseEntity.ok(progressService.getHomeWidget(currentUserId()));
	}

	private static HistoryPeriod parsePeriod(String period) {
		try {
			return HistoryPeriod.valueOf(period.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BadRequestAlertException("period must be one of DAY, WEEK, MONTH");
		}
	}

	private static Long currentUserId() {
		return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
