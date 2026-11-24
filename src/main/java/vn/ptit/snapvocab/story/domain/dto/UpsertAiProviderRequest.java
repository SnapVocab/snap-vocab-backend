package vn.ptit.snapvocab.story.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpsertAiProviderRequest {

	@NotBlank(message = "Provider name is required")
	@Size(max = 100)
	private String name;

	@NotNull(message = "Provider type is required")
	private AiProviderType providerType;

	@NotBlank(message = "Base URL is required")
	@Size(max = 500)
	private String baseUrl;

	/** Optional on update: leave blank to keep the existing encrypted API key. */
	private String apiKey;

	@NotBlank(message = "Model name is required")
	@Size(max = 150)
	private String modelName;

	@DecimalMin("0.0")
	@DecimalMax("2.0")
	private Double temperature;

	@Min(128)
	@Max(16384)
	private Integer maxTokens;

	@Min(2000)
	@Max(60000)
	private Integer timeoutMs;

	private Boolean enabled;

	@Min(1)
	@Max(999)
	private Integer priority;
}

