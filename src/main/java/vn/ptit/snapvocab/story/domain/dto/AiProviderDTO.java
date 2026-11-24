package vn.ptit.snapvocab.story.domain.dto;

import java.time.Instant;
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
public class AiProviderDTO {

	private Long id;
	private String name;
	private AiProviderType providerType;
	private String baseUrl;
	private String maskedApiKey;
	private boolean hasApiKey;
	private String modelName;
	private Double temperature;
	private Integer maxTokens;
	private Integer timeoutMs;
	private boolean enabled;
	private Integer priority;
	private Instant createdAt;
	private Instant updatedAt;
}

