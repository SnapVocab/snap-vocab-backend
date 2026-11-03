package vn.ptit.snapvocab.audit.service;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-free search text, same folding as the admin's foldVi (§5.4). */
public final class AuditText {

	private AuditText() {
	}

	/** "Bậc Thầy Hoàng Kim" → "bac thay hoang kim", "Đặng" → "dang". */
	public static String fold(String s) {
		String noMarks = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
	}

	/** LIKE pattern for q, escaping the wildcards with '!' (used as ESCAPE '!'). */
	public static String likePattern(String q) {
		String folded = fold(q.strip()).replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + folded + "%";
	}

	public static String cut(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
