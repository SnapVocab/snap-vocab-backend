package vn.ptit.snapvocab.audit.domain.dto;

/** Body of commands without a form (publish, archive). */
public record AuditReasonRequest(String reason) {
}
