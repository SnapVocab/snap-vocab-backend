package vn.ptit.snapvocab.vocab.domain.dto.topic;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class CreateTopicItemDTO {

	@JsonAnyGetter
	@JsonAnySetter
	@Builder.Default
	private Map<String, Object> groups = new LinkedHashMap<>();
}
