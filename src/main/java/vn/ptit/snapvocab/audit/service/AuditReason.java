package vn.ptit.snapvocab.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditErrorCode;
import vn.ptit.snapvocab.audit.exception.AuditException;

/** The admin's "why" (§4.5.4): required past a guardrail, on archive and on ban; 10..500 characters when given. */
public final class AuditReason {

	public static final int MIN = 10;
	public static final int MAX = 500;
	public static final String PRICE_OUT_OF_BAND = "PRICE_OUT_OF_BAND";
	public static final String DAILY_REWARD_OVER_CAP = "DAILY_REWARD_OVER_CAP";
	public static final String ARCHIVE = "ARCHIVE";
	public static final String BAN = "BAN";

	private AuditReason() {
	}

	/** Reads and removes "reason" from a JSON body copy (it is not an entity field). */
	public static String take(ObjectNode body) {
		JsonNode node = body.remove("reason");
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isTextual()) {
			throw new AuditException(AuditErrorCode.AUDIT_REASON_INVALID, "reason must be text", limits());
		}
		return node.asText();
	}

	/** Trimmed reason or null; throws when it is required and missing, or present but not 10..500 characters. */
	public static String check(String raw, boolean required, String rule, String guardrail) {
		String reason = raw == null || raw.isBlank() ? null : raw.strip();
		if (reason == null) {
			if (required) {
				Map<String, Object> details = new HashMap<>(); // Map.of rejects a null guardrail
				details.put("rule", rule);
				details.put("guardrail", guardrail);
				throw new AuditException(AuditErrorCode.AUDIT_REASON_REQUIRED, "reason required: " + rule, details);
			}
			return null;
		}
		if (reason.length() < MIN || reason.length() > MAX) {
			throw new AuditException(AuditErrorCode.AUDIT_REASON_INVALID,
					"reason must be " + MIN + ".." + MAX + " characters", limits());
		}
		return reason;
	}

	private static Map<String, Object> limits() {
		return Map.of("min", MIN, "max", MAX);
	}
}
