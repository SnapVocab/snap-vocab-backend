package vn.ptit.snapvocab.scan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanDetectionsDTO;

/** Labels and headwords a finished scan detected ({@code scan_requests.result_json}): the words it can be credited for. */
public final class ScanDetectedWords {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private ScanDetectedWords() {
	}

	public static List<String> of(String resultJson) {
		try {
			return MAPPER.readValue(resultJson, ScanDetectionsDTO.class).detections().stream()
					.flatMap(d -> Stream.of(d.label(), d.headword()))
					.filter(Objects::nonNull)
					.toList();
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Corrupt scan result", e);
		}
	}
}
