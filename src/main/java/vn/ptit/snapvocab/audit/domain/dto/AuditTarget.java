package vn.ptit.snapvocab.audit.domain.dto;

import java.util.List;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;

/** The object a write touched, built by the business service; {@code parents} only for topics (§4.4.4). */
public record AuditTarget(AuditTargetType type, String id, String name, String code, List<Long> parents) {

	public static AuditTarget of(AuditTargetType type, Object id, String name, String code) {
		return new AuditTarget(type, String.valueOf(id), name, code, null);
	}
}
