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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.story.domain.dto.AiModelOptionDTO;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;

@Component
@RequiredArgsConstructor
public class GeminiLlmAdapter implements LlmProviderAdapter {

	private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	@Override
	public boolean supports(AiProviderType type) {
		return type == AiProviderType.GEMINI;
	}

	@Override
	public String generateJson(ResolvedProvider provider, String systemPrompt, String userPrompt) {
		String base = normalizeBaseUrl(provider.baseUrl());
		String model = provider.modelName().startsWith("models/")
				? provider.modelName().substring("models/".length())
				: provider.modelName();
		String url = base + "/v1beta/models/" + model + ":generateContent?key=" + provider.apiKey();

		try {
			Map<String, Object> payload = Map.of(
					"systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
					"contents", List.of(Map.of(
							"role", "user",
							"parts", List.of(Map.of("text", userPrompt))
					)),
					"generationConfig", Map.of(
							"temperature", provider.temperature(),
							"maxOutputTokens", provider.maxTokens(),
							"responseMimeType", "application/json"
					)
			);

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofMillis(provider.timeoutMs()))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Gemini HTTP " + response.statusCode() + ": " + truncate(response.body()));
			}

			JsonNode root = objectMapper.readTree(response.body());
			JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
			if (textNode.isMissingNode() || textNode.asText().isBlank()) {
				throw new IllegalStateException("Gemini returned empty content");
			}
			return stripCodeFences(textNode.asText());
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Gemini request failed: " + e.getMessage(), e);
		}
	}

	@Override
	public List<AiModelOptionDTO> fetchModels(ResolvedProvider provider) {
		String base = normalizeBaseUrl(provider.baseUrl());
		String url = base + "/v1beta/models?pageSize=100&key=" + provider.apiKey();

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(12))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Gemini models HTTP " + response.statusCode() + ": " + truncate(response.body()));
			}

			JsonNode modelsNode = objectMapper.readTree(response.body()).path("models");
			List<AiModelOptionDTO> result = new ArrayList<>();
			if (modelsNode.isArray()) {
				for (JsonNode m : modelsNode) {
					boolean supportsGenerate = false;
					JsonNode methods = m.path("supportedGenerationMethods");
					if (methods.isArray()) {
						for (JsonNode method : methods) {
							if ("generateContent".equals(method.asText())) {
								supportsGenerate = true;
								break;
							}
						}
					}
					if (!supportsGenerate) {
						continue;
					}
					String rawName = m.path("name").asText("");
					String id = rawName.startsWith("models/") ? rawName.substring("models/".length()) : rawName;
					if (id.isBlank()) {
						continue;
					}
					String displayName = m.path("displayName").asText(id);
					result.add(AiModelOptionDTO.builder().id(id).displayName(displayName).build());
				}
			}
			result.sort(Comparator.comparing(AiModelOptionDTO::getId));
			return result;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to fetch Gemini models: " + e.getMessage(), e);
		}
	}

	private String normalizeBaseUrl(String baseUrl) {
		String b = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.trim();
		while (b.endsWith("/")) {
			b = b.substring(0, b.length() - 1);
		}
		return b;
	}

	static String stripCodeFences(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			if (firstNewline > 0) {
				trimmed = trimmed.substring(firstNewline + 1);
			}
			if (trimmed.endsWith("```")) {
				trimmed = trimmed.substring(0, trimmed.length() - 3);
			}
		}
		return trimmed.trim();
	}

	private String truncate(String s) {
		if (s == null) return "";
		return s.length() > 300 ? s.substring(0, 300) + "..." : s;
	}
}

