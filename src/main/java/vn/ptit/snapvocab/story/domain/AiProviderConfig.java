package vn.ptit.snapvocab.story.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;

@Entity
@Table(name = "ai_provider_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderConfig extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider_type", nullable = false, length = 30)
	private AiProviderType providerType;

	@Column(name = "base_url", nullable = false, length = 500)
	private String baseUrl;

	@Column(name = "api_key_encrypted", columnDefinition = "TEXT")
	private String apiKeyEncrypted;

	@Column(name = "model_name", nullable = false, length = 150)
	private String modelName;

	@Builder.Default
	@Column(nullable = false)
	private Double temperature = 0.7;

	@Builder.Default
	@Column(name = "max_tokens", nullable = false)
	private Integer maxTokens = 1500;

	@Builder.Default
	@Column(name = "timeout_ms", nullable = false)
	private Integer timeoutMs = 20000;

	@Builder.Default
	@Column(nullable = false)
	private boolean enabled = true;

	@Builder.Default
	@Column(nullable = false)
	private Integer priority = 1;
}

