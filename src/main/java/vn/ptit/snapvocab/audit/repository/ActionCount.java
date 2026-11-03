package vn.ptit.snapvocab.audit.repository;

import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;

/** SUM is null over no rows; read null as 0. */
public record ActionCount(AuditAction action, Long count, Long economy, Long guardrail) {
}
