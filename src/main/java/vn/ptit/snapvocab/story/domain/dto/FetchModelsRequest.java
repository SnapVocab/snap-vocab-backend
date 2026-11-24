package vn.ptit.snapvocab.story.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchModelsRequest {

	/** Existing provider ID (optional: reuses stored encrypted API key if apiKey is blank). */
	private Long providerId;
	private AiProviderType providerType;
	private String baseUrl;
	private String apiKey;
}

