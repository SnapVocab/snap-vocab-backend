package vn.ptit.snapvocab.audit.domain.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditArea;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditKind;
import vn.ptit.snapvocab.common.dto.Meta;

/** AuditLogResponse of snapAdmin/src/domains/admin-audit/types.ts (§5.1). */
public record AuditLogResponseDTO(List<AuditEntryDTO> data, Meta meta, Summary summary) {

	/** Over the period (and the target, if any), ignoring the other filters (§5.2). */
	public record Summary(long total, Map<AuditKind, Long> byKind, List<Day> daily, List<Area> areas,
			List<ActorCount> actors) {
	}

	public record Day(LocalDate day, long count) {
	}

	public record Area(AuditArea area, long count) {
	}

	/** {@code actor} null = the system. */
	public record ActorCount(AuditEntryDTO.Actor actor, long count) {
	}
}
