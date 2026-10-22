package vn.ptit.snapvocab.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.AdminAuditLog;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.repository.AdminAuditLogRepository;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.security.SecurityUtils;

/** Writes the audit row of an admin write (admin_audit_log_backend.md §4.2, PD-ADS-10). */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

	/** "Giá và phần thưởng" cell (§5.3); names as they appear in changes. */
	public static final Set<String> ECONOMY_FIELDS = Set.of("price", "purchasable", "rewardCoin", "rewardXp",
			"rewardItemCode", "rewardItemFallbackCoin");

	private final AdminAuditLogRepository repository;
	private final ObjectMapper objectMapper;

	/**
	 * One row in the write's transaction (MANDATORY: calling it outside one is a programming error). A failure here
	 * propagates and rolls the write back ("audit fails ⇒ the change fails"). Call it AFTER the object is saved and
	 * AFTER AuditReason.check.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(AuditAction action, AuditTarget target, List<AuditChange> changes, String reason,
			String guardrail, Long objectVersion) {
		if (target.type() != action.getTargetType()) {
			throw new IllegalArgumentException(action + " expects " + action.getTargetType() + ", got " + target.type());
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Long actorId = SecurityUtils.getCurrentUserId().orElse(null);
		String actorEmail = actorId == null ? null : SecurityUtils.getCurrentUserLogin().orElse(null);
		String name = AuditText.cut(target.name(), 255);
		repository.save(AdminAuditLog.builder()
				.createdAt(now)
				.vnDay(vnDay(now))
				.actorId(actorId)
				.actorEmail(actorEmail)
				.area(action.getArea())
				.action(action)
				.targetType(target.type())
				.targetId(target.id())
				.targetName(name)
				.targetCode(target.code())
				.targetParents(target.parents() == null ? null
						: target.parents().stream().map(String::valueOf).collect(Collectors.joining(",")))
				.changes(write(changes))
				.reason(reason)
				.guardrail(guardrail)
				.economyChange(!action.isCreateOrDelete()
						&& changes.stream().anyMatch(c -> ECONOMY_FIELDS.contains(c.field())))
				.objectVersion(objectVersion)
				.searchText(AuditText.cut(AuditText.fold(Stream.of(name, target.code(), target.id(), actorEmail, reason)
						.filter(Objects::nonNull).collect(Collectors.joining(" "))), 1500))
				.build());
		Metrics.counter("admin.audit.recorded", "area", action.getArea().name(), "action", action.name()).increment();
	}

	/** Calendar day in Vietnam (UTC+7, no DST) of an instant. */
	static LocalDate vnDay(Instant at) {
		return LocalDate.ofInstant(at, ProgressService.ZONE);
	}

	private String write(List<AuditChange> changes) {
		try {
			return objectMapper.writeValueAsString(changes);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("audit changes are not serializable", e);
		}
	}
}
