package vn.ptit.snapvocab.story.service;

import java.util.List;
import vn.ptit.snapvocab.story.domain.dto.AiModelOptionDTO;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;

public interface LlmProviderAdapter {

	boolean supports(AiProviderType type);

	String generateJson(ResolvedProvider provider, String systemPrompt, String userPrompt);

	List<AiModelOptionDTO> fetchModels(ResolvedProvider provider);

	record ResolvedProvider(
			String name,
			AiProviderType providerType,
			String baseUrl,
			String apiKey,
			String modelName,
			double temperature,
			int maxTokens,
			int timeoutMs
	) {}

	record LlmGenerationResult(
			String jsonContent,
			String providerName,
			String modelName,
			long latencyMs
	) {}
}

