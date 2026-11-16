package vn.ptit.snapvocab.adminusers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Activity;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Badge;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Ban;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Coin;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Detail;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Event;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Item;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.ListResponse;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Missions;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Progress;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Row;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Scan;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Scans;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Status;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Summary;
import vn.ptit.snapvocab.adminusers.dto.AdminUserDTOs.Wallet;
import vn.ptit.snapvocab.adminusers.repository.AdminUserRepository;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.audit.service.AuditReason;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.progress.domain.dto.ProgressSummaryDTO;
import vn.ptit.snapvocab.progress.repository.LearningEventRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.progress.service.StreakFreezeService;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanQuotaDTO;
import vn.ptit.snapvocab.scan.service.ScanDetectedWords;
import vn.ptit.snapvocab.scan.service.ScanQuotaService;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.auth.ForgotPasswordRequest;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.user.service.AuthenticationService;
import vn.ptit.snapvocab.user.service.AvatarUrlService;
import vn.ptit.snapvocab.user.service.RefreshTokenService;

/** Admin › Người học: read a learner, lock/unlock the account, send the app's password-reset email. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

	private static final Set<String> SORTS = Set.of("lastStudied", "createdAt", "streak", "words");
	private static final int MAX_PAGE_SIZE = 100;

	private final AdminUserRepository repository;
	private final UserRepository userRepository;
	private final ProgressService progressService;
	private final LearningEventRepository learningEventRepository;
	private final ScanQuotaService scanQuotaService;
	private final AvatarUrlService avatarUrlService;
	private final AdminAuditService auditService;
	private final RefreshTokenService refreshTokenService;
	private final AuthenticationService authenticationService;
	private final ObjectMapper objectMapper;

	@Transactional(readOnly = true)
	public ListResponse list(String q, String status, String activity, String sort, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BadRequestAlertException("page must be >= 0 and size 1.." + MAX_PAGE_SIZE);
		}
		String s = sort == null ? "lastStudied" : sort;
		if (!SORTS.contains(s)) {
			throw new BadRequestAlertException("sort must be one of " + SORTS);
		}
		String st = filter(status, Status.class);
		String act = filter(activity, Activity.class);
		String needle = q == null || q.isBlank() ? null
				: q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		Instant now = Economy.now();
		Instant now7 = now.minus(Duration.ofDays(7));
		Instant now30 = now.minus(Duration.ofDays(30));
		List<Row> rows = repository.page(needle, st, act, s, now7, now30, yesterdayStart(), size, page * size)
				.stream().map(r -> row(r, null)).toList();
		long total = repository.count(needle, st, act, now7, now30);
		AdminUserRepository.Summary sum = repository.summary(now7, now30);
		return new ListResponse(rows,
				Meta.builder().page((long) page).size((long) size).total(total)
						.pages(Math.max(1, (total + size - 1) / size)).build(),
				new Summary(sum.getTotal(), sum.getActive7d(), sum.getDormant30d(), sum.getNever(), sum.getPending(),
						sum.getBanned()));
	}

	@Transactional(readOnly = true)
	public Detail detail(Long id) {
		AdminUserRepository.Row r = repository.row(id, yesterdayStart()).orElseThrow(() -> notFound(id));
		User user = userRepository.findById(id).orElseThrow(() -> notFound(id));
		LocalDate today = StreakFreezeService.today();
		Row row = row(r, (long) progressService.getStreak(id).currentStreak());

		ProgressSummaryDTO p = progressService.getSummary(id);
		Progress progress = new Progress(Math.max(0, p.totalWords() - p.learnedCount()),
				Math.max(0, p.learnedCount() - p.reviewingCount() - p.masteredCount()), p.reviewingCount(),
				p.masteredCount(), p.dueCount(), p.completedQuizCount(),
				p.accuracy() == null ? null : Math.round(p.accuracy() * 100));

		Map<LocalDate, Long> perDay = new HashMap<>();
		learningEventRepository.countPerDay(id, today.minusDays(29), today)
				.forEach(d -> perDay.put(d.getLearningDate(), d.getEventCount()));
		List<Long> activity = new ArrayList<>();
		for (int i = 29; i >= 0; i--) {
			activity.add(perDay.getOrDefault(today.minusDays(i), 0L));
		}

		AdminUserRepository.Missions m = repository.missions(id, today.minusDays(6), today);
		List<Badge> badges = repository.badges(id).stream().map(b -> new Badge(b.getCode(), b.getName(), b.getAt()))
				.toList();
		List<Coin> coins = repository.recentCoins(id).stream()
				.map(c -> new Coin(c.getAt(), c.getAmount(), c.getSource(), coinNote(c.getSource(), c.getNote())))
				.toList();
		List<Item> items = repository.items(id).stream()
				.map(i -> new Item(i.getCode(), i.getName(), i.getType(), i.getEquipped() != 0, i.getQuantity()))
				.toList();

		ScanQuotaDTO quota = scanQuotaService.quota(id);
		AdminUserRepository.ScanTotals totals = repository.scanTotals(id, Economy.now().minus(Duration.ofDays(30)));
		List<Scan> scans = repository.recentScans(id).stream()
				.map(s -> new Scan(s.getAt(), s.getStatus(), s.getErrorCode(),
						"DONE".equals(s.getStatus()) ? ScanDetectedWords.of(s.getResultJson()).stream().distinct().toList()
								: List.of(),
						s.getSavedWords()))
				.toList();

		Ban ban = user.getBannedAt() == null ? null
				: new Ban(user.getBanReason(), iso(user.getBannedAt()), user.getBannedBy());
		return new Detail(row, user.getNativeLanguage(), user.getLearningLanguage(), ban, progress, activity,
				new Missions(m.getAssigned(), m.getCompleted(), m.getClaimed()), badges, new Wallet(coins), items,
				new Scans(quota.used(), quota.limit(), totals.getTotal(), totals.getFailed(), scans),
				timeline(row, ban, perDay, today, coins, scans, badges, repository.recentQuizzes(id)));
	}

	/** Locks the account: no new tokens (AuthenticationService), refresh tokens revoked; learning data kept. */
	@Transactional
	public Detail ban(Long id, String rawReason) {
		String reason = AuditReason.check(rawReason, true, AuditReason.BAN, null);
		User user = learnerForUpdate(id);
		if (user.getBannedAt() != null) {
			throw new ResourceAlreadyExistException("Learner " + id + " is already banned");
		}
		String before = user.isActivated() ? "ACTIVE" : "PENDING";
		user.setBannedAt(Economy.now());
		user.setBanReason(reason);
		user.setBannedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
		userRepository.save(user);
		auditService.record(AuditAction.LEARNER_BANNED, target(user),
				List.of(AuditDiff.change(objectMapper, "accountStatus", before, "BANNED")), reason, null, null);
		// ponytail: an access token already issued lives out its TTL (15 min); add a per-request ban check if
		// that window matters.
		refreshTokenService.revokeAllTokens(user.getEmail());
		return detail(id);
	}

	@Transactional
	public Detail unban(Long id, String rawReason) {
		String reason = AuditReason.check(rawReason, false, AuditReason.BAN, null);
		User user = learnerForUpdate(id);
		if (user.getBannedAt() == null) {
			throw new ResourceAlreadyExistException("Learner " + id + " is not banned");
		}
		user.setBannedAt(null);
		user.setBanReason(null);
		user.setBannedBy(null);
		userRepository.save(user);
		auditService.record(AuditAction.LEARNER_UNBANNED, target(user), List.of(AuditDiff.change(objectMapper,
				"accountStatus", "BANNED", user.isActivated() ? "ACTIVE" : "PENDING")), reason, null, null);
		return detail(id);
	}

	/** The app's forgot-password email (a code), so the learner sets the new password; 400 before email verify. */
	@Transactional
	public void sendPasswordReset(Long id) {
		User user = learnerForUpdate(id);
		authenticationService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));
		auditService.record(AuditAction.LEARNER_PASSWORD_RESET_SENT, target(user), List.of(), null, null, null);
	}

	private User learnerForUpdate(Long id) {
		User user = userRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
		if (user.getAuthorities().stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()))) {
			throw notFound(id); // admins are not learners: never listed, shown or locked here
		}
		return user;
	}

	private Row row(AdminUserRepository.Row r, Long streak) {
		return new Row(r.getId(), r.getDisplayName(), r.getEmail(), avatarUrlService.resolve(r.getAvatarKey()),
				Status.valueOf(r.getStatus()), r.getCreatedAt(), r.getLastStudiedAt(),
				streak == null ? r.getStreakDays() : streak, r.getLongestStreakDays(), r.getWordsSaved(), r.getCoin(),
				r.getExp());
	}

	/** At most 10 recent events, newest first, from what the profile already loaded. */
	private static List<Event> timeline(Row row, Ban ban, Map<LocalDate, Long> perDay, LocalDate today,
			List<Coin> coins, List<Scan> scans, List<Badge> badges, List<AdminUserRepository.QuizRow> quizzes) {
		List<Event> out = new ArrayList<>();
		out.add(new Event(row.createdAt(), "ACCOUNT", "Tạo tài khoản"));
		if (ban != null) {
			out.add(new Event(ban.at(), "ACCOUNT", "Bị khóa: " + ban.reason()));
		}
		if (row.lastStudiedAt() != null) {
			LocalDate day = LocalDate.ofInstant(Instant.parse(row.lastStudiedAt()), ProgressService.ZONE);
			long n = day.isAfter(today.minusDays(30)) ? perDay.getOrDefault(day, 0L) : 0;
			out.add(new Event(row.lastStudiedAt(), "STUDY", n > 0 ? "Học " + n + " lượt trong ngày" : "Học"));
		}
		quizzes.forEach(q -> out.add(new Event(q.getAt(), "QUIZ", "Hoàn thành quiz " + q.getTotal() + " câu · "
				+ (q.getTotal() == 0 ? 0 : Math.round(100.0 * q.getCorrect() / q.getTotal())) + "%")));
		scans.stream().limit(2).forEach(s -> out.add(new Event(s.at(), "SCAN", "FAILED".equals(s.status())
				? "Scan lỗi: " + s.errorCode()
				: "Scan: " + String.join(", ", s.labels()) + " · lưu " + s.savedWords() + " từ")));
		coins.stream().limit(3).forEach(c -> out.add(new Event(c.at(), switch (c.source()) {
			case "SHOP_PURCHASE" -> "PURCHASE";
			case "BADGE_REWARD" -> "BADGE";
			default -> "MISSION";
		}, c.note() + " (" + (c.amount() > 0 ? "+" : "") + c.amount() + " Coin)")));
		badges.stream().skip(Math.max(0, badges.size() - 2))
				.forEach(b -> out.add(new Event(b.earnedAt(), "BADGE", "Đạt huy hiệu " + b.name())));
		return out.stream().filter(e -> e.at() != null).sorted(Comparator.comparing(Event::at).reversed()).limit(10)
				.toList();
	}

	private static String coinNote(String source, String note) {
		if (note != null && !note.isBlank()) {
			return note;
		}
		return switch (source) {
			case "MISSION_REWARD" -> "Thưởng nhiệm vụ";
			case "BADGE_REWARD" -> "Thưởng huy hiệu";
			case "SHOP_PURCHASE" -> "Mua vật phẩm";
			default -> "Điều chỉnh Coin";
		};
	}

	/** "ALL" or blank = no filter; anything else must name a value of {@code type}. */
	private static <E extends Enum<E>> String filter(String value, Class<E> type) {
		if (value == null || value.isBlank() || "ALL".equals(value)) {
			return null;
		}
		try {
			return Enum.valueOf(type, value).name();
		} catch (IllegalArgumentException e) {
			throw new BadRequestAlertException("Unknown " + type.getSimpleName().toLowerCase() + ": " + value);
		}
	}

	private static Instant yesterdayStart() {
		return StreakFreezeService.today().minusDays(1).atStartOfDay(ProgressService.ZONE).toInstant();
	}

	private static String iso(Instant at) {
		return at.truncatedTo(ChronoUnit.SECONDS).toString();
	}

	private static AuditTarget target(User user) {
		String name = (nonNull(user.getFirstName()) + " " + nonNull(user.getLastName())).strip();
		return AuditTarget.of(AuditTargetType.LEARNER, user.getId(), name.isEmpty() ? user.getEmail() : name, null);
	}

	private static String nonNull(String s) {
		return s == null ? "" : s;
	}

	private static ResourceNotFoundException notFound(Long id) {
		return new ResourceNotFoundException("Learner " + id + " not found");
	}
}
