package vn.ptit.snapvocab.badge.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.audit.service.AuditReason;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.dto.AdminBadgeDTO;
import vn.ptit.snapvocab.badge.domain.dto.RefillEstimateDTO;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeErrorCode;
import vn.ptit.snapvocab.badge.exception.BadgeException;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

/**
 * Admin catalog (badges.md §8.4, §10): P1 reads, P2 edits the display fields and switches badges on/off. Never syncs,
 * grants or touches user_badges. One row's {@code version} is enough: unlike missions, no rule spans several rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBadgeService {

	static final int NAME_MAX = 100;
	static final int DESCRIPTION_MAX = 255;
	/** Icons the app draws (badges.md §8.4); anything else would fall back to "award" on the learner's screen. */
	static final Set<String> ICON_KEYS = Set.of("flame", "clipboard-check", "target", "book-open", "award");
	/** §5.3: the condition is immutable; a new threshold is a new badge. */
	static final List<String> LOCKED = List.of("code", "metric", "threshold");
	static final Set<String> EDITABLE = Set.of("name", "description", "iconKey", "sortOrder", "isActive");
	/** Audit "changes", in form order. */
	static final List<String> AUDIT_FIELDS = List.of("name", "description", "iconKey", "sortOrder", "isActive");

	private final BadgeRepository badgeRepository;
	private final UserBadgeRepository userBadgeRepository;
	private final ObjectMapper objectMapper;
	private final AdminAuditService audit;

	/** Editable state; JSON names match AdminBadgeDTO. */
	record Form(String name, String description, String iconKey, Integer sortOrder,
			@JsonProperty("isActive") Boolean isActive) {
	}

	public record Violation(String field, BadgeErrorCode code, String message) {
	}

	// ---- queries ----

	public List<AdminBadgeDTO> list() {
		List<Badge> catalog = badgeRepository.findAllByOrderBySortOrderAscIdAsc();
		if (catalog.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> counts = userBadgeRepository.countEarnersByBadge().stream()
				.collect(Collectors.toMap(UserBadgeRepository.BadgeEarnerCount::getBadgeId,
						UserBadgeRepository.BadgeEarnerCount::getEarnerCount));
		return catalog.stream().map(b -> AdminBadgeDTO.of(b, counts.getOrDefault(b.getId(), 0L))).toList();
	}

	public AdminBadgeDTO get(Long id) {
		return AdminBadgeDTO.of(find(id), userBadgeRepository.countByBadgeId(id));
	}

	/**
	 * PD-BDG-13d: read-only count of activated accounts that reached the threshold and do not hold the badge. Grants
	 * nothing; badges are only granted by each learner's sync. One aggregate query, per metric.
	 */
	public RefillEstimateDTO refillEstimate(Long id) {
		Badge b = find(id);
		long eligible = switch (b.getMetric()) {
			case LONGEST_STREAK_DAYS -> badgeRepository.countRefillLongestStreak(b.getId(), b.getThreshold());
			case QUIZ_COMPLETED -> badgeRepository.countRefillQuizCompleted(b.getId(), b.getThreshold());
			case PERFECT_QUIZ -> badgeRepository.countRefillPerfectQuiz(b.getId(), b.getThreshold());
			case DISTINCT_WORDS_ADDED -> badgeRepository.countRefillDistinctWords(b.getId(), b.getThreshold());
		};
		return new RefillEstimateDTO(eligible, Instant.now().truncatedTo(ChronoUnit.MICROS), b.getVersion());
	}

	// ---- commands ----

	/**
	 * P2 (§8.4): display fields and/or {@code isActive}, with the {@code version} the admin read. Editing content keeps
	 * every earner's badge, earned date and seen state (PD-BDG-13b); switching on/off creates or removes no grant. A body
	 * that changes nothing saves and records nothing. Optional {@code reason} goes to the activity log.
	 */
	@Transactional
	public AdminBadgeDTO patch(Long id, JsonNode body) {
		Badge badge = find(id);
		if (body == null || !body.isObject()) {
			throw invalid(List.of(violation("body", "JSON object required")));
		}
		ObjectNode request = ((ObjectNode) body).deepCopy();
		String reason = AuditReason.check(AuditReason.take(request), false, null, null);
		List<String> locked = LOCKED.stream().filter(request::has).toList();
		if (!locked.isEmpty()) {
			throw new BadgeException(BadgeErrorCode.FIELD_LOCKED, "Immutable fields: " + locked, Map.of("fields", locked));
		}
		JsonNode version = request.remove("version");
		if (version == null || !version.isIntegralNumber()) {
			throw invalid(List.of(violation("version", "version is required")));
		}
		if (version.asLong() != badge.getVersion()) {
			throw conflict(badge.getVersion());
		}

		List<Violation> errors = new ArrayList<>();
		request.properties().forEach(e -> {
			if (!EDITABLE.contains(e.getKey())) {
				errors.add(violation(e.getKey(), "not an editable field"));
			}
		});
		Form before = formOf(badge);
		Form next = new Form(
				text(request, "name", before.name(), NAME_MAX, errors),
				text(request, "description", before.description(), DESCRIPTION_MAX, errors),
				iconKey(request, before.iconKey(), errors),
				sortOrder(request, before.sortOrder(), errors),
				isActive(request, before.isActive(), errors));
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
		List<AuditChange> changes = AuditDiff.of(objectMapper, before, next, AUDIT_FIELDS);
		if (changes.isEmpty()) {
			return get(id);
		}
		badge.setName(next.name());
		badge.setDescription(next.description());
		badge.setIconKey(next.iconKey());
		badge.setSortOrder(next.sortOrder());
		badge.setActive(next.isActive());
		Badge saved;
		try {
			saved = badgeRepository.saveAndFlush(badge);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw conflict(null);
		}
		AuditAction action = before.isActive().equals(saved.getActive()) ? AuditAction.BADGE_UPDATED
				: saved.getActive() ? AuditAction.BADGE_ENABLED : AuditAction.BADGE_DISABLED;
		audit.record(action, AuditTarget.of(AuditTargetType.BADGE, saved.getId(), saved.getName(), saved.getCode()),
				changes, reason, null, saved.getVersion());
		return AdminBadgeDTO.of(saved, userBadgeRepository.countByBadgeId(id));
	}

	// ---- field rules (§8.4) ----

	/** Trimmed before the length check, so what is stored is what was validated. */
	private static String text(ObjectNode body, String field, String current, int max, List<Violation> errors) {
		if (!body.has(field)) {
			return current;
		}
		JsonNode v = body.get(field);
		String s = v.isTextual() ? v.asText().strip() : null;
		if (s == null || s.isEmpty() || s.length() > max) {
			errors.add(violation(field, "1.." + max + " characters, not blank"));
			return current;
		}
		return s;
	}

	private static String iconKey(ObjectNode body, String current, List<Violation> errors) {
		if (!body.has("iconKey")) {
			return current;
		}
		JsonNode v = body.get("iconKey");
		if (!v.isTextual() || !ICON_KEYS.contains(v.asText())) {
			errors.add(violation("iconKey", "must be one of " + ICON_KEYS.stream().sorted().toList()));
			return current;
		}
		return v.asText();
	}

	private static Integer sortOrder(ObjectNode body, Integer current, List<Violation> errors) {
		if (!body.has("sortOrder")) {
			return current;
		}
		JsonNode v = body.get("sortOrder");
		if (!v.isIntegralNumber() || !v.canConvertToInt()) {
			errors.add(violation("sortOrder", "integer required"));
			return current;
		}
		return v.asInt();
	}

	private static Boolean isActive(ObjectNode body, Boolean current, List<Violation> errors) {
		if (!body.has("isActive")) {
			return current;
		}
		JsonNode v = body.get("isActive");
		if (!v.isBoolean()) {
			errors.add(violation("isActive", "true or false"));
			return current;
		}
		return v.asBoolean();
	}

	// ---- helpers ----

	static Form formOf(Badge b) {
		return new Form(b.getName(), b.getDescription(), b.getIconKey(), b.getSortOrder(), b.getActive());
	}

	private Badge find(Long id) {
		return badgeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("BADGE_NOT_FOUND"));
	}

	private static Violation violation(String field, String message) {
		return new Violation(field, BadgeErrorCode.VALIDATION_ERROR, message);
	}

	private static BadgeException invalid(List<Violation> errors) {
		Violation first = errors.get(0);
		return new BadgeException(BadgeErrorCode.VALIDATION_ERROR, first.field() + ": " + first.message(),
				Map.of("errors", errors));
	}

	/** {@code currentVersion} null when a concurrent writer slipped in after our read (the flush lost the race). */
	private static BadgeException conflict(Long currentVersion) {
		return new BadgeException(BadgeErrorCode.VERSION_CONFLICT, "Badge was modified by someone else; reload it",
				currentVersion == null ? null : Map.of("currentVersion", currentVersion));
	}
}
