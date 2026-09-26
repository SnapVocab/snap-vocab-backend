package vn.ptit.snapvocab.vocab.domain.enumeration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DataType {
	TEXT,
	AUDIO,
	IMAGE,
	NUMBER,
	BOOLEAN,
	DATE;

	@JsonCreator
	public static DataType fromString(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (DataType type : DataType.values()) {
			if (type.name().equalsIgnoreCase(value.trim())) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown DataType: " + value);
	}

	@JsonValue
	public String toValue() {
		return name();
	}
}

