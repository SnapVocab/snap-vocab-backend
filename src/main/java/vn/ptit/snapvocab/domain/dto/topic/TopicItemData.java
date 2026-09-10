package vn.ptit.snapvocab.domain.dto.topic;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object representing a single vocabulary/topic item in the EAV model.
 * <p>
 * Uses {@link JsonAnyGetter} to dynamically unwrap group properties (e.g. "main", "examples")
 * directly into the top-level JSON object:
 * <ul>
 *   <li>Single groups ({@code multiple = false}): formatted as {@code Map<String, Object>} (e.g. {@code "main": { ... }})</li>
 *   <li>Multiple groups ({@code multiple = true}): formatted as {@code List<Map<String, Object>>} (e.g. {@code "examples": [ ... ]})</li>
 * </ul>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicItemData {

	private Long topicItemId;
	private Instant createdAt;

	/**
	 * Dynamic map holding attribute groups keyed by group name (e.g., "main", "examples").
	 * Unwrapped into root JSON properties via Jackson's {@code @JsonAnyGetter} and {@code @JsonAnySetter}.
	 */
	@JsonAnyGetter
	@JsonAnySetter
	@Builder.Default
	private Map<String, Object> groups = new LinkedHashMap<>();
}
