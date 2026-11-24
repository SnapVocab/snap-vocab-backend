package vn.ptit.snapvocab.story.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestAiProviderResponse {

	private boolean ok;
	private long latencyMs;
	private String sampleOutput;
	private String error;
}

