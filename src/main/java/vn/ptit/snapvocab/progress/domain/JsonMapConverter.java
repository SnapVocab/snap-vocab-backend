package vn.ptit.snapvocab.progress.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

/**
 * Map as JSON text. Unlike {@code SqlTypes.JSON}, a text column passes ddl-auto: validate on both MySQL (whose
 * existing {@code json} columns report LONGVARCHAR) and MariaDB (where {@code json} is an alias of LONGTEXT).
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Map<String, Object> value) {
		try {
			return value == null ? null : MAPPER.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public Map<String, Object> convertToEntityAttribute(String json) {
		try {
			return json == null ? null : MAPPER.readValue(json, TYPE);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
