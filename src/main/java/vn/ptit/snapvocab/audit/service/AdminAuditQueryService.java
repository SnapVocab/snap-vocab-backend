package vn.ptit.snapvocab.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.AdminAuditLog;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditEntryDTO;
import vn.ptit.snapvocab.audit.domain.dto.AuditLogResponseDTO;
import vn.ptit.snapvocab.audit.domain.dto.AuditLogResponseDTO.ActorCount;
import vn.ptit.snapvocab.audit.domain.dto.AuditLogResponseDTO.Area;
import vn.ptit.snapvocab.audit.domain.dto.AuditLogResponseDTO.Day;
import vn.ptit.snapvocab.audit.domain.dto.AuditLogResponseDTO.Summary;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditArea;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditErrorCode;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditKind;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.exception.AuditException;
import vn.ptit.snapvocab.audit.repository.ActionCount;
import vn.ptit.snapvocab.audit.repository.AdminAuditLogRepository;
import vn.ptit.snapvocab.audit.repository.DayCount;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.progress.service.ProgressService;

/** GET /api/admin/audit-log (§5): rows with every filter, summary over the base set only. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditQueryService {

	static final Set<Integer> PERIODS = Set.of(7, 30, 90);
	private static final List<AuditAction> LIFECYCLE = Arrays.stream(AuditAction.values())
			.filter(AuditAction::isLifecycle).toList();
	private static final TypeReference<List<AuditChange>> CHANGES = new TypeReference<>() {
	};

	private final AdminAuditLogRepository repository;
	private final ObjectMapper objectMapper;

	public AuditLogResponseDTO list(int days, LocalDate day, AuditArea area, String actor, AuditKind kind,
			AuditTargetType targetType, String targetId, String q, int page, int size) {
		LocalDate to = LocalDate.now(ProgressService.ZONE);
		LocalDate from = to.minusDays(days - 1L);
		List<Map<String, String>> errors = new ArrayList<>();
		if (!PERIODS.contains(days)) {
			errors.add(error("days", "7, 30 or 90"));
		}
		if (day != null && (day.isBefore(from) || day.isAfter(to))) {
			errors.add(error("day", "must be inside the period"));
		}
		boolean hasTargetId = targetId != null && !targetId.isBlank();
		if ((targetType != null) != hasTargetId) {
			errors.add(error("targetId", "targetType and targetId go together"));
		}
		if (hasTargetId && targetId.length() > 255) {
			errors.add(error("targetId", "at most 255 characters"));
		}
		if (q != null && q.strip().length() > 100) {
			errors.add(error("q", "at most 100 characters"));
		}
		if (page < 0) {
			errors.add(error("page", ">= 0"));
		}
		if (size < 1 || size > 100) {
			errors.add(error("size", "1..100"));
		}
		boolean anyActor = actor == null || actor.isBlank();
		Long actorId = null;
		if (!anyActor && !"SYSTEM".equals(actor)) {
			try {
				actorId = Long.parseLong(actor);
			} catch (NumberFormatException e) {
				actorId = -1L;
			}
			if (actorId <= 0) {
				errors.add(error("actor", "SYSTEM or an admin id"));
			}
		}
		if (!errors.isEmpty()) {
			throw new AuditException(AuditErrorCode.AUDIT_FILTER_INVALID, "invalid filter", Map.of("errors", errors));
		}
		String id = targetType == null ? null : targetId;
		Page<AdminAuditLog> rows = repository.search(from, to, targetType, id, day, area, anyActor, actorId,
				kind == null ? null : kind.name(), LIFECYCLE, q == null || q.isBlank() ? null : AuditText.likePattern(q),
				PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		Meta meta = Meta.builder().page((long) page).size((long) size).total(rows.getTotalElements())
				.pages((long) rows.getTotalPages()).build();
		return new AuditLogResponseDTO(rows.getContent().stream().map(this::toDto).toList(), meta,
				summary(from, to, targetType, id));
	}

	/** §5.2: one GROUP BY action folded in Java, one per day (every day filled), one per actor. */
	private Summary summary(LocalDate from, LocalDate to, AuditTargetType targetType, String targetId) {
		List<ActionCount> byAction = repository.countByAction(from, to, targetType, targetId);
		long total = 0;
		Map<AuditKind, Long> byKind = new LinkedHashMap<>();
		Arrays.stream(AuditKind.values()).forEach(k -> byKind.put(k, 0L));
		Map<AuditArea, Long> areas = new EnumMap<>(AuditArea.class);
		for (ActionCount c : byAction) {
			long n = c.count();
			total += n;
			byKind.merge(AuditKind.PRICE_REWARD, orZero(c.economy()), Long::sum);
			byKind.merge(AuditKind.GUARDRAIL, orZero(c.guardrail()), Long::sum);
			if (c.action().isLifecycle()) {
				byKind.merge(AuditKind.LIFECYCLE, n, Long::sum);
			}
			if (c.action().getArea() == AuditArea.LEARNERS) {
				byKind.merge(AuditKind.LEARNER_ACCOUNT, n, Long::sum);
			}
			areas.merge(c.action().getArea(), n, Long::sum);
		}
		Map<LocalDate, Long> perDay = repository.countByDay(from, to, targetType, targetId).stream()
				.collect(Collectors.toMap(DayCount::day, DayCount::count));
		List<Day> daily = from.datesUntil(to.plusDays(1)).map(d -> new Day(d, perDay.getOrDefault(d, 0L))).toList();
		List<Area> areaList = areas.entrySet().stream()
				.filter(e -> e.getValue() > 0)
				.sorted(Map.Entry.<AuditArea, Long>comparingByValue().reversed())
				.map(e -> new Area(e.getKey(), e.getValue()))
				.toList();
		List<ActorCount> actors = repository.countByActor(from, to, targetType, targetId).stream()
				.map(a -> new ActorCount(a.actorId() == null ? null : new AuditEntryDTO.Actor(a.actorId(), a.email()),
						a.count()))
				.sorted(Comparator.comparingLong(ActorCount::count).reversed())
				.toList();
		return new Summary(total, byKind, daily, areaList, actors);
	}

	private AuditEntryDTO toDto(AdminAuditLog row) {
		List<Long> parents = row.getTargetParents() == null ? null
				: Arrays.stream(row.getTargetParents().split(",")).map(Long::valueOf).toList();
		return new AuditEntryDTO(row.getId(), row.getCreatedAt(),
				row.getActorId() == null ? null : new AuditEntryDTO.Actor(row.getActorId(), row.getActorEmail()),
				row.getArea(), row.getAction(),
				new AuditEntryDTO.Target(row.getTargetType(), row.getTargetId(), row.getTargetName(), row.getTargetCode(),
						parents),
				readChanges(row), row.getReason(), row.getGuardrail(), row.getObjectVersion());
	}

	private List<AuditChange> readChanges(AdminAuditLog row) {
		try {
			return objectMapper.readValue(row.getChanges(), CHANGES);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("audit row " + row.getId() + " has invalid changes JSON", e);
		}
	}

	private static long orZero(Long n) {
		return n == null ? 0 : n;
	}

	private static Map<String, String> error(String field, String message) {
		return Map.of("field", field, "message", message);
	}
}
