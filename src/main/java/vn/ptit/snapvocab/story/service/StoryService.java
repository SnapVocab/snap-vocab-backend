package vn.ptit.snapvocab.story.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.story.config.StoryProperties;
import vn.ptit.snapvocab.story.domain.AiStory;
import vn.ptit.snapvocab.story.domain.dto.GenerateStoryRequest;
import vn.ptit.snapvocab.story.domain.dto.StoryDTO;
import vn.ptit.snapvocab.story.domain.dto.StoryListResponse;
import vn.ptit.snapvocab.story.domain.dto.SubmitStoryClozeRequest;
import vn.ptit.snapvocab.story.domain.dto.SubmitStoryClozeResponse;
import vn.ptit.snapvocab.story.domain.enumeration.StoryGenre;
import vn.ptit.snapvocab.story.domain.enumeration.StoryLevel;
import vn.ptit.snapvocab.story.domain.enumeration.StoryMode;
import vn.ptit.snapvocab.story.repository.AiStoryRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;

@Service
@RequiredArgsConstructor
public class StoryService {

	private static final Logger LOG = LoggerFactory.getLogger(StoryService.class);
	private static final Pattern BLANK_PATTERN = Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");

	private final AiStoryRepository storyRepository;
	private final TopicItemRepository topicItemRepository;
	private final UserRepository userRepository;
	private final LlmGatewayService llmGatewayService;
	private final StoryProperties storyProperties;
	private final ObjectMapper objectMapper;

	@Transactional
	public StoryDTO generateStory(Long userId, GenerateStoryRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));

		StoryListResponse.QuotaDTO quota = computeQuota(userId);
		if (quota.getRemaining() <= 0) {
			throw new IllegalStateException("You have reached today's AI Story limit (" + quota.getDailyLimit() + "/day).");
		}

		List<String> words = resolveTargetWords(request);
		int minWords = Math.max(1, storyProperties.getMinWords());
		int maxWords = Math.max(minWords, storyProperties.getMaxWords());
		if (words.size() < minWords) {
			throw new IllegalArgumentException("Please provide at least " + minWords + " vocabulary words.");
		}
		if (words.size() > maxWords) {
			words = words.subList(0, maxWords);
		}

		StoryMode mode = request.getMode() != null ? request.getMode() : StoryMode.BLENDED;
		StoryLevel level = request.getLevel() != null ? request.getLevel() : StoryLevel.A2;
		StoryGenre genre = request.getGenre() != null ? request.getGenre() : StoryGenre.DAILY;

		String systemPrompt = buildSystemPrompt(mode);
		String userPrompt = buildUserPrompt(words, mode, level, genre);

		LlmProviderAdapter.LlmGenerationResult result = llmGatewayService.generateJson(systemPrompt, userPrompt);
		ParsedStory parsed = parseStoryJson(result.jsonContent(), words);

		AiStory entity = AiStory.builder()
				.user(user)
				.topicId(request.getTopicId())
				.title(parsed.title())
				.storyMode(mode)
				.level(level)
				.genre(genre)
				.providerName(result.providerName())
				.modelName(result.modelName())
				.targetWordsJson(toJson(parsed.targetWords()))
				.sentencesJson(toJson(parsed.sentences()))
				.totalBlanks(parsed.totalBlanks())
				.xpAwarded(0)
				.build();

		AiStory saved = storyRepository.save(entity);
		return toDto(saved, parsed.targetWords(), parsed.sentences());
	}

	@Transactional(readOnly = true)
	public StoryListResponse listStories(Long userId, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.min(Math.max(1, size), 50);
		Page<AiStory> result = storyRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize));
		List<StoryDTO> items = result.getContent().stream().map(this::toDto).toList();
		return StoryListResponse.builder()
				.items(items)
				.page(result.getNumber())
				.size(result.getSize())
				.totalElements(result.getTotalElements())
				.totalPages(result.getTotalPages())
				.quota(computeQuota(userId))
				.build();
	}

	@Transactional(readOnly = true)
	public StoryDTO getStory(Long userId, Long id) {
		AiStory story = storyRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new IllegalArgumentException("Story not found"));
		return toDto(story);
	}

	@Transactional
	public SubmitStoryClozeResponse submitCloze(Long userId, Long id, SubmitStoryClozeRequest request) {
		AiStory story = storyRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new IllegalArgumentException("Story not found"));

		List<StoryDTO.StorySentenceDTO> sentences = fromJson(
				story.getSentencesJson(),
				new TypeReference<List<StoryDTO.StorySentenceDTO>>() {}
		);

		Map<Integer, String> expectedByIndex = new LinkedHashMap<>();
		for (StoryDTO.StorySentenceDTO sentence : sentences) {
			if (sentence.getBlanks() == null) {
				continue;
			}
			for (StoryDTO.StoryBlankDTO blank : sentence.getBlanks()) {
				expectedByIndex.put(blank.getBlankIndex(), blank.getAnswer());
			}
		}

		Map<Integer, String> submittedByIndex = new HashMap<>();
		for (SubmitStoryClozeRequest.BlankAnswer ans : request.getAnswers()) {
			submittedByIndex.put(ans.getBlankIndex(), ans.getValue() != null ? ans.getValue().trim() : "");
		}

		List<SubmitStoryClozeResponse.BlankResult> results = new ArrayList<>();
		int correctCount = 0;
		for (Map.Entry<Integer, String> entry : expectedByIndex.entrySet()) {
			int blankIndex = entry.getKey();
			String expected = entry.getValue() != null ? entry.getValue().trim() : "";
			String submitted = submittedByIndex.getOrDefault(blankIndex, "");
			boolean correct = !expected.isEmpty() && expected.equalsIgnoreCase(submitted);
			if (correct) {
				correctCount++;
			}
			results.add(SubmitStoryClozeResponse.BlankResult.builder()
					.blankIndex(blankIndex)
					.submitted(submitted)
					.expected(expected)
					.correct(correct)
					.build());
		}

		int totalBlanks = expectedByIndex.size();
		int accuracy = totalBlanks > 0 ? Math.round((correctCount * 100.0f) / totalBlanks) : 0;
		int earnedTargetXp = accuracy >= 80 ? 30 : accuracy >= 50 ? 20 : 10;
		int previousXp = story.getXpAwarded() != null ? story.getXpAwarded() : 0;
		int xpDelta = Math.max(0, earnedTargetXp - previousXp);

		if (story.getBestScore() == null || accuracy > story.getBestScore()) {
			story.setBestScore(accuracy);
		}
		if (xpDelta > 0) {
			story.setXpAwarded(previousXp + xpDelta);
			User user = userRepository.findById(userId).orElse(null);
			if (user != null) {
				user.setExp(user.getExp() + xpDelta);
				userRepository.save(user);
			}
		}
		if (story.getCompletedAt() == null) {
			story.setCompletedAt(Instant.now());
		}
		storyRepository.save(story);

		return SubmitStoryClozeResponse.builder()
				.storyId(story.getId())
				.correctCount(correctCount)
				.totalBlanks(totalBlanks)
				.accuracyPercent(accuracy)
				.xpAwardedNow(xpDelta)
				.totalXpAwarded(story.getXpAwarded())
				.bestScore(story.getBestScore() != null ? story.getBestScore() : accuracy)
				.results(results)
				.build();
	}

	@Transactional
	public void deleteStory(Long userId, Long id) {
		AiStory story = storyRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new IllegalArgumentException("Story not found"));
		storyRepository.delete(story);
	}

	private StoryListResponse.QuotaDTO computeQuota(Long userId) {
		ZoneId zone = ZoneId.of(storyProperties.getZone());
		LocalDate today = LocalDate.now(zone);
		Instant start = today.atStartOfDay(zone).toInstant();
		Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
		int used = (int) storyRepository.countByUserIdAndCreatedAtBetween(userId, start, end);
		int limit = Math.max(1, storyProperties.getQuotaPerDay());
		return StoryListResponse.QuotaDTO.builder()
				.usedToday(used)
				.dailyLimit(limit)
				.remaining(Math.max(0, limit - used))
				.minWords(storyProperties.getMinWords())
				.maxWords(storyProperties.getMaxWords())
				.build();
	}

	private List<String> resolveTargetWords(GenerateStoryRequest request) {
		Map<String, String> deduped = new LinkedHashMap<>();

		if (request.getWords() != null) {
			for (String raw : request.getWords()) {
				addWord(deduped, raw);
			}
		}

		if (deduped.isEmpty() && request.getTopicId() != null) {
			List<TopicItem> items = topicItemRepository.findByTopicIdWithEav(request.getTopicId());
			for (TopicItem item : items) {
				String extracted = extractPrimaryWord(item);
				addWord(deduped, extracted);
				if (deduped.size() >= storyProperties.getMaxWords()) {
					break;
				}
			}
		}

		return new ArrayList<>(deduped.values());
	}

	private void addWord(Map<String, String> deduped, String raw) {
		if (raw == null) {
			return;
		}
		String cleaned = raw.trim().replaceAll("\\s+", " ");
		if (cleaned.isEmpty() || cleaned.length() > 60) {
			return;
		}
		String key = cleaned.toLowerCase(Locale.ROOT);
		deduped.putIfAbsent(key, cleaned);
	}

	private String extractPrimaryWord(TopicItem item) {
		if (item == null || item.getGroupInstances() == null) {
			return null;
		}
		String fallback = null;
		for (TopicItemAttributeGroup group : item.getGroupInstances()) {
			if (group.getValues() == null) {
				continue;
			}
			for (TopicItemAttributeValue value : group.getValues()) {
				String val = value.getValue();
				if (val == null || val.isBlank()) {
					continue;
				}
				String attrName = value.getAttribute() != null && value.getAttribute().getName() != null
						? value.getAttribute().getName().toLowerCase(Locale.ROOT)
						: "";
				if (attrName.contains("word") || attrName.contains("term") || attrName.contains("vocab") || attrName.contains("front")) {
					return val.trim();
				}
				if (fallback == null) {
					fallback = val.trim();
				}
			}
		}
		return fallback;
	}

	private String buildSystemPrompt(StoryMode mode) {
		if (mode == StoryMode.ENGLISH) {
			return """
					You are an engaging English language storyteller for Vietnamese learners.
					Write a vivid, coherent short story in natural English that weaves ALL target English vocabulary words naturally into the plot.
					Wrap every occurrence of each target vocabulary word (or its natural inflected form) in double square brackets like [[word]].
					Provide a natural Vietnamese translation for each sentence.
					Always return strictly valid JSON with NO markdown fences and NO extra commentary.
					""";
		}
		return """
				You are a creative Vietnamese-English "Truyện Chêm" (Macaronic storytelling) author.
				Write a lively, relatable, natural Vietnamese short story where EVERY target English vocabulary word is seamlessly woven directly into the Vietnamese sentences in English, wrapped in double square brackets like [[word]].
				Do NOT put Vietnamese translations in parentheses right next to the English word — the surrounding Vietnamese context must make the meaning crystal clear so the learner absorbs the word naturally.
				For each sentence's "translation" field, provide a clean, natural full-English version of that sentence.
				Always return strictly valid JSON with NO markdown fences and NO extra commentary.
				""";
	}

	private String buildUserPrompt(List<String> words, StoryMode mode, StoryLevel level, StoryGenre genre) {
		String wordList = String.join(", ", words);
		int sentenceCount = Math.max(5, Math.min(12, words.size() + 2));
		return """
				Create a story with the following parameters:
				- Mode: %s
				- CEFR Level: %s
				- Genre: %s
				- Target English words (%d words — MUST use ALL of them at least once wrapped in [[word]]): %s
				- Length: %d to %d sentences organized into 2-3 short paragraphs (paragraph 1, 2, 3).

				Return strictly valid JSON matching this exact schema:
				{
				  "title": "Short catchy title",
				  "targetWords": [
				    {
				      "word": "original English word",
				      "meaning": "concise Vietnamese meaning",
				      "phonetic": "/IPA/",
				      "partOfSpeech": "n. / v. / adj. / adv."
				    }
				  ],
				  "sentences": [
				    {
				      "index": 1,
				      "paragraph": 1,
				      "text": "Sentence containing [[targetWord]] naturally...",
				      "translation": "Companion translation sentence"
				    }
				  ]
				}
				""".formatted(mode.name(), level.name(), genre.name(), words.size(), wordList, sentenceCount, sentenceCount + 2);
	}

	private ParsedStory parseStoryJson(String rawContent, List<String> fallbackWords) {
		String cleaned = stripCodeFence(rawContent);
		try {
			JsonNode root = objectMapper.readTree(cleaned);
			String title = root.path("title").asText("Untitled Context Story").trim();
			if (title.isEmpty()) {
				title = "Untitled Context Story";
			}
			if (title.length() > 180) {
				title = title.substring(0, 180);
			}

			List<StoryDTO.TargetWordDTO> targetWords = new ArrayList<>();
			JsonNode wordsNode = root.path("targetWords");
			if (wordsNode.isArray()) {
				for (JsonNode n : wordsNode) {
					String w = n.path("word").asText("").trim();
					if (!w.isEmpty()) {
						targetWords.add(StoryDTO.TargetWordDTO.builder()
								.word(w)
								.meaning(n.path("meaning").asText("").trim())
								.phonetic(n.path("phonetic").asText("").trim())
								.partOfSpeech(n.path("partOfSpeech").asText("").trim())
								.build());
					}
				}
			}
			if (targetWords.isEmpty()) {
				for (String w : fallbackWords) {
					targetWords.add(StoryDTO.TargetWordDTO.builder().word(w).build());
				}
			}

			List<StoryDTO.StorySentenceDTO> sentences = new ArrayList<>();
			int blankCounter = 0;
			JsonNode sentencesNode = root.path("sentences");
			if (sentencesNode.isArray()) {
				int idx = 1;
				for (JsonNode n : sentencesNode) {
					String text = n.path("text").asText("").trim();
					if (text.isEmpty()) {
						continue;
					}
					int paragraph = n.path("paragraph").asInt(1);
					String translation = n.path("translation").asText("").trim();

					List<StoryDTO.StoryBlankDTO> blanks = new ArrayList<>();
					Matcher matcher = BLANK_PATTERN.matcher(text);
					while (matcher.find()) {
						String token = matcher.group(1).trim();
						if (!token.isEmpty()) {
							blankCounter++;
							blanks.add(StoryDTO.StoryBlankDTO.builder()
									.blankIndex(blankCounter)
									.answer(token)
									.displayWord(token)
									.build());
						}
					}

					sentences.add(StoryDTO.StorySentenceDTO.builder()
							.index(n.path("index").asInt(idx))
							.paragraph(Math.max(1, paragraph))
							.text(text)
							.translation(translation)
							.blanks(blanks)
							.build());
					idx++;
				}
			}

			if (sentences.isEmpty()) {
				throw new IllegalStateException("AI model returned a story with no sentences.");
			}

			return new ParsedStory(title, targetWords, sentences, blankCounter);
		} catch (Exception ex) {
			LOG.error("Failed to parse LLM story JSON: {}", rawContent, ex);
			throw new IllegalStateException("AI returned malformed story format. Please try again.", ex);
		}
	}

	private String stripCodeFence(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim();
		if (s.startsWith("```")) {
			int firstNewline = s.indexOf('\n');
			if (firstNewline > 0) {
				s = s.substring(firstNewline + 1);
			}
			if (s.endsWith("```")) {
				s = s.substring(0, s.length() - 3);
			}
		}
		int startBrace = s.indexOf('{');
		int endBrace = s.lastIndexOf('}');
		if (startBrace >= 0 && endBrace > startBrace) {
			s = s.substring(startBrace, endBrace + 1);
		}
		return s.trim();
	}

	private StoryDTO toDto(AiStory story) {
		List<StoryDTO.TargetWordDTO> targetWords = fromJson(
				story.getTargetWordsJson(),
				new TypeReference<List<StoryDTO.TargetWordDTO>>() {}
		);
		List<StoryDTO.StorySentenceDTO> sentences = fromJson(
				story.getSentencesJson(),
				new TypeReference<List<StoryDTO.StorySentenceDTO>>() {}
		);
		return toDto(story, targetWords, sentences);
	}

	private StoryDTO toDto(
			AiStory story,
			List<StoryDTO.TargetWordDTO> targetWords,
			List<StoryDTO.StorySentenceDTO> sentences
	) {
		return StoryDTO.builder()
				.id(story.getId())
				.topicId(story.getTopicId())
				.title(story.getTitle())
				.mode(story.getStoryMode())
				.level(story.getLevel())
				.genre(story.getGenre())
				.providerName(story.getProviderName())
				.modelName(story.getModelName())
				.targetWords(targetWords)
				.sentences(sentences)
				.totalBlanks(story.getTotalBlanks() != null ? story.getTotalBlanks() : 0)
				.bestScore(story.getBestScore())
				.xpAwarded(story.getXpAwarded() != null ? story.getXpAwarded() : 0)
				.completedAt(story.getCompletedAt())
				.createdAt(story.getCreatedAt())
				.build();
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize story JSON", ex);
		}
	}

	private <T> List<T> fromJson(String json, TypeReference<List<T>> typeRef) {
		if (json == null || json.isBlank()) {
			return Collections.emptyList();
		}
		try {
			return objectMapper.readValue(json, typeRef);
		} catch (Exception ex) {
			LOG.warn("Failed to deserialize story JSON: {}", ex.getMessage());
			return Collections.emptyList();
		}
	}

	private record ParsedStory(
			String title,
			List<StoryDTO.TargetWordDTO> targetWords,
			List<StoryDTO.StorySentenceDTO> sentences,
			int totalBlanks
	) {}
}
