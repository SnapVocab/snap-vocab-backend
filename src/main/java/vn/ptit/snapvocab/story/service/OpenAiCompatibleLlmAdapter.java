package vn.ptit.snapvocab.story.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.story.domain.dto.AiModelOptionDTO;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleLlmAdapter implements LlmProviderAdapter {

	private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	@Override
	public boolean supports(AiProviderType type) {
		return type == AiProviderType.OPENAI_COMPATIBLE;
	}

	@Override
	public String generateJson(ResolvedProvider provider, String systemPrompt, String userPrompt) {
		String base = normalizeBaseUrl(provider.baseUrl());
		String url = base + "/chat/completions";

		try {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("model", provider.modelName());
			payload.put("temperature", provider.temperature());
			payload.put("max_tokens", provider.maxTokens());
			payload.put("response_format", Map.of("type", "json_object"));
			payload.put("messages", List.of(
					Map.of("role", "system", "content", systemPrompt),
					Map.of("role", "user", "content", userPrompt)
			));

			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofMillis(provider.timeoutMs()))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

			if (provider.apiKey() != null && !provider.apiKey().isBlank()) {
				reqBuilder.header("Authorization", "Bearer " + provider.apiKey().trim());
			}

			HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("OpenAI-compatible HTTP " + response.statusCode() + ": " + truncate(response.body()));
			}

			JsonNode root = objectMapper.readTree(response.body());
			JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
			if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
				throw new IllegalStateException("OpenAI-compatible provider returned empty content");
			}
			return GeminiLlmAdapter.stripCodeFences(contentNode.asText());
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("OpenAI-compatible request failed: " + e.getMessage(), e);
		}
	}

	@Override
	public List<AiModelOptionDTO> fetchModels(ResolvedProvider provider) {
		String base = normalizeBaseUrl(provider.baseUrl());
		String url = base + "/models";

		try {
			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(12))
					.header("Accept", "application/json")
					.GET();

			if (provider.apiKey() != null && !provider.apiKey().isBlank()) {
				reqBuilder.header("Authorization", "Bearer " + provider.apiKey().trim());
			}

			HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Models HTTP " + response.statusCode() + ": " + truncate(response.body()));
			}

			JsonNode root = objectMapper.readTree(response.body());
			JsonNode dataNode = root.path("data");
			List<AiModelOptionDTO> result = new ArrayList<>();
			if (dataNode.isArray()) {
				for (JsonNode m : dataNode) {
					String id = m.path("id").asText("").trim();
					if (id.isBlank()) {
						continue;
					}
					String displayName = m.path("name").asText("").trim();
					if (displayName.isBlank()) {
						displayName = m.path("owned_by").asText("").trim();
						displayName = displayName.isBlank() ? id : id + " (" + displayName + ")";
					}
					result.add(AiModelOptionDTO.builder().id(id).displayName(displayName).build());
				}
			}
			result.sort(Comparator.comparing(AiModelOptionDTO::getId));
			return result;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to fetch models: " + e.getMessage(), e);
		}
	}

	private String normalizeBaseUrl(String baseUrl) {
		String b = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.trim();
		while (b.endsWith("/")) {
			b = b.substring(0, b.length() - 1);
		}
		return b;
	}

	private String truncate(String s) {
		if (s == null) return "";
		return s.length() > 300 ? s.substring(0, 300) + "..." : s;
	}
}

