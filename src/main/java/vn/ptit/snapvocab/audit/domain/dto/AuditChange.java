package vn.ptit.snapvocab.audit.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** One changed field, values as Jackson writes them; the admin formats them (§4.3). */
public record AuditChange(String field, JsonNode before, JsonNode after) {
}
