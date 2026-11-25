package vn.ptit.snapvocab.story.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.story.config.StoryProperties;
import vn.ptit.snapvocab.story.domain.AiProviderConfig;
import vn.ptit.snapvocab.story.domain.dto.AiModelOptionDTO;
import vn.ptit.snapvocab.story.domain.dto.TestAiProviderResponse;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;
import vn.ptit.snapvocab.story.repository.AiProviderConfigRepository;

@Service
@RequiredArgsConstructor
public class LlmGatewayService {

	private static final Logger LOG = LoggerFactory.getLogger(LlmGatewayService.class);
	private static final int MIN_STORY_MAX_TOKENS = 4096;
	private static final int MIN_STORY_TIMEOUT_MS = 30000;

	private final AiProviderConfigRepository providerRepository;
	private final ApiKeyCryptoService cryptoService;
	private final List<LlmProviderAdapter> adapters;
	private final StoryProperties storyProperties;

	public record ValidatedGeneration<T>(
			T parsed,
			String providerName,
			String modelName,
			long latencyMs
	) {}

	public LlmProviderAdapter.LlmGenerationResult generateJson(String systemPrompt, String userPrompt) {
		ValidatedGeneration<String> res = generateAndParse(systemPrompt, userPrompt, Function.identity());
		return new LlmProviderAdapter.LlmGenerationResult(
				res.parsed(),
				res.providerName(),
				res.modelName(),
				res.latencyMs()
		);
	}

	/**
	 * Executes the active provider chain in priority order and runs {@code parser} inside the loop so
	 * truncated or malformed JSON from Provider #1 automatically falls back to Provider #2 / #3.
	 */
	public <T> ValidatedGeneration<T> generateAndParse(
			String systemPrompt,
			String userPrompt,
			Function<String, T> parser
	) {
		List<LlmProviderAdapter.ResolvedProvider> chain = resolveActiveProviders();
		if (chain.isEmpty()) {
			throw new IllegalStateException("No AI provider is configured or enabled. Please configure one in Admin AI Providers.");
		}

		RuntimeException lastError = null;
		for (LlmProviderAdapter.ResolvedProvider provider : chain) {
			long start = System.currentTimeMillis();
			try {
				LlmProviderAdapter adapter = findAdapter(provider.providerType());
				String content = adapter.generateJson(provider, systemPrompt, userPrompt);
				T parsed = parser.apply(content);
				long latency = System.currentTimeMillis() - start;
				LOG.info("LLM generation & validation succeeded via provider='{}' model='{}' in {}ms",
						provider.name(), provider.modelName(), latency);
				return new ValidatedGeneration<>(parsed, provider.name(), provider.modelName(), latency);
			} catch (RuntimeException ex) {
				long latency = System.currentTimeMillis() - start;
				LOG.warn("LLM provider '{}' ({}) failed after {}ms: {} — falling back if available",
						provider.name(), provider.modelName(), latency, ex.getMessage());
				lastError = ex;
			}
		}

		throw new IllegalStateException(
				"All configured AI providers failed: " + (lastError != null ? lastError.getMessage() : "unknown error"),
				lastError);
	}

	public List<AiModelOptionDTO> fetchModels(AiProviderType type, String baseUrl, String rawApiKey) {
		LlmProviderAdapter adapter = findAdapter(type);
		LlmProviderAdapter.ResolvedProvider temp = new LlmProviderAdapter.ResolvedProvider(
				"fetch-models",
				type,
				baseUrl,
				rawApiKey,
				"",
				0.2,
				256,
				12000
		);
		return adapter.fetchModels(temp);
	}

	public TestAiProviderResponse testProvider(AiProviderConfig config) {
		long start = System.currentTimeMillis();
		try {
			LlmProviderAdapter.ResolvedProvider resolved = toResolved(config, false);
			LlmProviderAdapter adapter = findAdapter(resolved.providerType());
			String sample = adapter.generateJson(
					resolved,
					"You are a health check bot. Always respond with valid JSON.",
					"Return {\"status\":\"ok\",\"message\":\"connected\"}"
			);
			return TestAiProviderResponse.builder()
					.ok(true)
					.latencyMs(System.currentTimeMillis() - start)
					.sampleOutput(sample.length() > 200 ? sample.substring(0, 200) : sample)
					.build();
		} catch (Exception ex) {
			return TestAiProviderResponse.builder()
					.ok(false)
					.latencyMs(System.currentTimeMillis() - start)
					.error(ex.getMessage())
					.build();
		}
	}

	private List<LlmProviderAdapter.ResolvedProvider> resolveActiveProviders() {
		List<AiProviderConfig> configs = providerRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
		List<LlmProviderAdapter.ResolvedProvider> resolved = new ArrayList<>();
		for (AiProviderConfig c : configs) {
			resolved.add(toResolved(c, true));
		}

		if (resolved.isEmpty()
				&& storyProperties.getFallbackGeminiApiKey() != null
				&& !storyProperties.getFallbackGeminiApiKey().isBlank()) {
			resolved.add(new LlmProviderAdapter.ResolvedProvider(
					"Env Gemini Fallback",
					AiProviderType.GEMINI,
					"https://generativelanguage.googleapis.com",
					storyProperties.getFallbackGeminiApiKey().trim(),
					storyProperties.getFallbackGeminiModel(),
					0.7,
					MIN_STORY_MAX_TOKENS,
					MIN_STORY_TIMEOUT_MS
			));
		}
		return resolved;
	}

	private LlmProviderAdapter.ResolvedProvider toResolved(AiProviderConfig config, boolean enforceStoryHeadroom) {
		int configuredTokens = config.getMaxTokens() != null ? config.getMaxTokens() : MIN_STORY_MAX_TOKENS;
		int configuredTimeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : MIN_STORY_TIMEOUT_MS;
		return new LlmProviderAdapter.ResolvedProvider(
				config.getName(),
				config.getProviderType(),
				config.getBaseUrl(),
				cryptoService.decrypt(config.getApiKeyEncrypted()),
				config.getModelName(),
				config.getTemperature() != null ? config.getTemperature() : 0.7,
				enforceStoryHeadroom ? Math.max(MIN_STORY_MAX_TOKENS, configuredTokens) : Math.max(512, configuredTokens),
				enforceStoryHeadroom ? Math.max(MIN_STORY_TIMEOUT_MS, configuredTimeout) : Math.max(12000, configuredTimeout)
		);
	}

	private LlmProviderAdapter findAdapter(AiProviderType type) {
		return adapters.stream()
				.filter(a -> a.supports(type))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unsupported AI provider type: " + type));
	}
}
