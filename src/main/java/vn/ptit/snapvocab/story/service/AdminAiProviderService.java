package vn.ptit.snapvocab.story.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.story.domain.AiProviderConfig;
import vn.ptit.snapvocab.story.domain.dto.AiModelOptionDTO;
import vn.ptit.snapvocab.story.domain.dto.AiProviderDTO;
import vn.ptit.snapvocab.story.domain.dto.FetchModelsRequest;
import vn.ptit.snapvocab.story.domain.dto.TestAiProviderResponse;
import vn.ptit.snapvocab.story.domain.dto.UpsertAiProviderRequest;
import vn.ptit.snapvocab.story.domain.enumeration.AiProviderType;
import vn.ptit.snapvocab.story.repository.AiProviderConfigRepository;

@Service
@RequiredArgsConstructor
public class AdminAiProviderService {

	private final AiProviderConfigRepository repository;
	private final ApiKeyCryptoService cryptoService;
	private final LlmGatewayService llmGatewayService;

	@Transactional(readOnly = true)
	public List<AiProviderDTO> listAll() {
		return repository.findAllByOrderByPriorityAscIdAsc().stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional
	public AiProviderDTO create(UpsertAiProviderRequest req) {
		AiProviderConfig entity = AiProviderConfig.builder()
				.name(req.getName().trim())
				.providerType(req.getProviderType())
				.baseUrl(req.getBaseUrl().trim())
				.apiKeyEncrypted(cryptoService.encrypt(req.getApiKey()))
				.modelName(req.getModelName().trim())
				.temperature(req.getTemperature() != null ? req.getTemperature() : 0.7)
				.maxTokens(req.getMaxTokens() != null ? req.getMaxTokens() : 1500)
				.timeoutMs(req.getTimeoutMs() != null ? req.getTimeoutMs() : 20000)
				.enabled(req.getEnabled() == null || req.getEnabled())
				.priority(req.getPriority() != null ? req.getPriority() : 1)
				.build();
		return toDto(repository.save(entity));
	}

	@Transactional
	public AiProviderDTO update(Long id, UpsertAiProviderRequest req) {
		AiProviderConfig entity = findOrThrow(id);
		entity.setName(req.getName().trim());
		entity.setProviderType(req.getProviderType());
		entity.setBaseUrl(req.getBaseUrl().trim());
		if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
			entity.setApiKeyEncrypted(cryptoService.encrypt(req.getApiKey()));
		}
		entity.setModelName(req.getModelName().trim());
		if (req.getTemperature() != null) {
			entity.setTemperature(req.getTemperature());
		}
		if (req.getMaxTokens() != null) {
			entity.setMaxTokens(req.getMaxTokens());
		}
		if (req.getTimeoutMs() != null) {
			entity.setTimeoutMs(req.getTimeoutMs());
		}
		if (req.getEnabled() != null) {
			entity.setEnabled(req.getEnabled());
		}
		if (req.getPriority() != null) {
			entity.setPriority(req.getPriority());
		}
		return toDto(repository.save(entity));
	}

	@Transactional
	public void delete(Long id) {
		AiProviderConfig entity = findOrThrow(id);
		repository.delete(entity);
	}

	@Transactional(readOnly = true)
	public List<AiModelOptionDTO> fetchModels(FetchModelsRequest req) {
		AiProviderType type = req.getProviderType();
		String baseUrl = req.getBaseUrl();
		String rawApiKey = req.getApiKey();

		if (req.getProviderId() != null) {
			AiProviderConfig existing = findOrThrow(req.getProviderId());
			if (type == null) {
				type = existing.getProviderType();
			}
			if (baseUrl == null || baseUrl.isBlank()) {
				baseUrl = existing.getBaseUrl();
			}
			if (rawApiKey == null || rawApiKey.isBlank()) {
				rawApiKey = cryptoService.decrypt(existing.getApiKeyEncrypted());
			}
		}

		if (type == null) {
			throw new IllegalArgumentException("providerType is required to fetch models");
		}
		return llmGatewayService.fetchModels(type, baseUrl, rawApiKey);
	}

	@Transactional(readOnly = true)
	public TestAiProviderResponse test(Long id) {
		return llmGatewayService.testProvider(findOrThrow(id));
	}

	private AiProviderConfig findOrThrow(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("AI provider not found with id: " + id));
	}

	private AiProviderDTO toDto(AiProviderConfig e) {
		String decrypted = cryptoService.decrypt(e.getApiKeyEncrypted());
		boolean hasKey = decrypted != null && !decrypted.isBlank();
		return AiProviderDTO.builder()
				.id(e.getId())
				.name(e.getName())
				.providerType(e.getProviderType())
				.baseUrl(e.getBaseUrl())
				.maskedApiKey(hasKey ? cryptoService.mask(decrypted) : "")
				.hasApiKey(hasKey)
				.modelName(e.getModelName())
				.temperature(e.getTemperature())
				.maxTokens(e.getMaxTokens())
				.timeoutMs(e.getTimeoutMs())
				.enabled(e.isEnabled())
				.priority(e.getPriority())
				.createdAt(e.getCreatedAt())
				.updatedAt(e.getUpdatedAt())
				.build();
	}
}

