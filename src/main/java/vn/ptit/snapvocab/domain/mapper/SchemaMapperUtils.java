package vn.ptit.snapvocab.domain.mapper;

public final class SchemaMapperUtils {

	private SchemaMapperUtils() {
	}

	public static String resolveLabel(String dbLabel, String name) {
		if (dbLabel != null && !dbLabel.isBlank()) {
			return dbLabel.trim();
		}
		return formatNameAsLabel(name);
	}

	public static String formatNameAsLabel(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String part : name.split("_")) {
			if (!part.isEmpty()) {
				if (sb.length() > 0) {
					sb.append(" ");
				}
				sb.append(Character.toUpperCase(part.charAt(0)));
				if (part.length() > 1) {
					sb.append(part.substring(1));
				}
			}
		}
		return sb.toString();
	}
}
