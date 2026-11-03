package vn.ptit.snapvocab.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.ArrayList;
import java.util.List;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;

/** The "changes" of an audit row (§4.3). Use Spring's ObjectMapper: a plain one writes Instant as a number. */
public final class AuditDiff {

	private AuditDiff() {
	}

	/**
	 * Fields whose JSON value differs, in {@code fields} order. A null side means "did not exist":
	 * before = null → creation (every non-null field of after), after = null → deletion (snapshot of before).
	 */
	public static List<AuditChange> of(ObjectMapper om, Object before, Object after, List<String> fields) {
		JsonNode b = before == null ? null : om.valueToTree(before);
		JsonNode a = after == null ? null : om.valueToTree(after);
		List<AuditChange> out = new ArrayList<>();
		for (String field : fields) {
			JsonNode bv = value(b, field);
			JsonNode av = value(a, field);
			if (!bv.equals(av)) {
				out.add(new AuditChange(field, bv, av));
			}
		}
		return out;
	}

	/** One field changed by a command that has no form (publish, archive): status DRAFT → PUBLISHED. */
	public static AuditChange change(ObjectMapper om, String field, Object before, Object after) {
		return new AuditChange(field, node(om, before), node(om, after));
	}

	public static boolean touches(List<AuditChange> changes, String... fields) {
		return changes.stream().anyMatch(c -> List.of(fields).contains(c.field()));
	}

	private static JsonNode value(JsonNode node, String field) {
		JsonNode v = node == null ? null : node.get(field);
		return v == null ? NullNode.instance : v;
	}

	private static JsonNode node(ObjectMapper om, Object value) {
		return value == null ? NullNode.instance : om.valueToTree(value);
	}
}
