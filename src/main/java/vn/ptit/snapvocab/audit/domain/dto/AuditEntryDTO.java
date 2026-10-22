package vn.ptit.snapvocab.audit.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditArea;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;

/** AuditEntry of snapAdmin/src/domains/admin-audit/types.ts. */
public record AuditEntryDTO(Long id, Instant at, Actor actor, AuditArea area, AuditAction action, Target target,
		List<AuditChange> changes, String reason, String guardrail, Long version) {

	public record Actor(Long id, String email) {
	}

	public record Target(AuditTargetType type, String id, String name, String code,
			@JsonInclude(JsonInclude.Include.NON_NULL) List<Long> parents) {
	}
}
