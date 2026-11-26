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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import vn.ptit.snapvocab.story.domain.dto.StoryTopicSummaryDTO;
import vn.ptit.snapvocab.story.domain.dto.StoryTopicWordsResponse;
import vn.ptit.snapvocab.story.domain.dto.SubmitStoryClozeRequest;
import vn.ptit.snapvocab.story.domain.dto.SubmitStoryClozeResponse;
import vn.ptit.snapvocab.story.domain.enumeration.StoryGenre;
import vn.ptit.snapvocab.story.repository.AiStoryRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

@Service
@RequiredArgsConstructor
public class StoryService {

	private static final Logger LOG = LoggerFactory.getLogger(StoryService.class);
	private static final Pattern BLANK_PATTERN = Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");
	private static final Pattern BOLD_MARKER_PATTERN = Pattern.compile("\\*\\*([^*\\[\\]\\n]{1,50})\\*\\*");
	private static final int DEFAULT_TOPIC_AUTO_PICK = 6;

	private final AiStoryRepository storyRepository;
	private final TopicItemAttributeValueRepository topicValueRepository;
	private final TopicItemRepository topicItemRepository;
	private final TemplateFieldRepository templateFieldRepository;
	private final TopicRepository topicRepository;
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

		List<String> words = resolveTargetWords(userId, request);
		int minWords = Math.max(1, storyProperties.getMinWords());
		int maxWords = Math.max(minWords, storyProperties.getMaxWords());
		if (words.size() < minWords) {
			throw new IllegalArgumentException("Please provide at least " + minWords + " vocabulary words.");
		}
		if (words.size() > maxWords) {
			words = new ArrayList<>(words.subList(0, maxWords));
		}

		StoryGenre genre = request.getGenre() != null ? request.getGenre() : StoryGenre.DAILY;

		String systemPrompt = buildSystemPrompt();
		String userPrompt = buildUserPrompt(words, genre);

		final List<String> finalWords = List.copyOf(words);
		LlmGatewayService.ValidatedGeneration<ParsedStory> generation = llmGatewayService.generateAndParse(
				systemPrompt,
				userPrompt,
				rawJson -> parseStoryJson(rawJson, finalWords)
		);
		ParsedStory parsed = generation.parsed();

		AiStory entity = AiStory.builder()
				.user(user)
				.topicId(request.getTopicId())
				.title(parsed.title())
				.genre(genre)
				.providerName(generation.providerName())
				.modelName(generation.modelName())
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

	@Transactional(readOnly = true)
	public List<StoryTopicSummaryDTO> listSystemTopics() {
		List<Topic> systemTopics = topicRepository.findAllSystemTopics();
		if (systemTopics.isEmpty()) {
			return List.of();
		}

		List<Long> allIds = systemTopics.stream().map(Topic::getId).toList();
		Map<Long, Long> directCounts = new HashMap<>();
		for (TopicItemRepository.TopicWordCount row : topicItemRepository.countByTopicIds(allIds)) {
			directCounts.put(row.getTopicId(), row.getTotal() != null ? row.getTotal() : 0L);
		}

		Map<Long, List<Long>> childrenByParent = new HashMap<>();
		for (Topic t : systemTopics) {
			if (t.getParent() != null && t.getParent().getId() != null) {
				childrenByParent.computeIfAbsent(t.getParent().getId(), k -> new ArrayList<>()).add(t.getId());
			}
		}

		int minWords = Math.max(1, storyProperties.getMinWords());
		Map<Long, Long> totalCountMemo = new HashMap<>();
		List<StoryTopicSummaryDTO> result = new ArrayList<>();

		for (Topic t : systemTopics) {
			long count = computeRecursiveWordCount(t.getId(), directCounts, childrenByParent, totalCountMemo, new HashSet<>());
			if (count < minWords) {
				continue;
			}
			result.add(StoryTopicSummaryDTO.builder()
					.id(t.getId())
					.name(t.getName())
					.translation(t.getTranslation())
					.collectionId(t.getCollection().getId())
					.collectionName(t.getCollection().getName())
					.parentId(t.getParent() != null ? t.getParent().getId() : null)
					.parentName(t.getParent() != null ? t.getParent().getName() : null)
					.wordCount((int) Math.min(Integer.MAX_VALUE, count))
					.build());
		}

		return result;
	}

	@Transactional(readOnly = true)
	public StoryTopicWordsResponse getSystemTopicWords(Long topicId) {
		Topic topic = requireSystemTopic(topicId);
		List<StoryTopicWordsResponse.TopicWordItem> words = extractTopicWordItems(topic);
		return StoryTopicWordsResponse.builder()
				.topicId(topic.getId())
				.topicName(topic.getName())
				.topicTranslation(topic.getTranslation())
				.words(words)
				.build();
	}

	private long computeRecursiveWordCount(
			Long topicId,
			Map<Long, Long> directCounts,
			Map<Long, List<Long>> childrenByParent,
			Map<Long, Long> memo,
			Set<Long> visiting
	) {
		if (memo.containsKey(topicId)) {
			return memo.get(topicId);
		}
		if (!visiting.add(topicId)) {
			return 0L;
		}
		long sum = directCounts.getOrDefault(topicId, 0L);
		for (Long childId : childrenByParent.getOrDefault(topicId, List.of())) {
			sum += computeRecursiveWordCount(childId, directCounts, childrenByParent, memo, visiting);
		}
		visiting.remove(topicId);
		memo.put(topicId, sum);
		return sum;
	}

	private Topic requireSystemTopic(Long topicId) {
		Topic topic = topicRepository.findById(topicId)
				.orElseThrow(() -> new IllegalArgumentException("System topic not found"));
		if (topic.getCollection() == null || topic.getCollection().getType() != CollectionType.SYSTEM) {
			throw new IllegalArgumentException("Only curated system topics are supported for AI Stories");
		}
		return topic;
	}

	private List<StoryTopicWordsResponse.TopicWordItem> extractTopicWordItems(Topic rootTopic) {
		List<Topic> topicTree = new ArrayList<>();
		collectTopics(rootTopic, topicTree);
		if (topicTree.isEmpty()) {
			return List.of();
		}

		Set<Long> targetAttrIds = new HashSet<>();
		Set<Long> translationAttrIds = new HashSet<>();
		Set<Long> visitedTemplateIds = new HashSet<>();

		for (Topic t : topicTree) {
			if (t.getActiveTemplate() == null || t.getActiveTemplate().getId() == null) {
				continue;
			}
			Long templateId = t.getActiveTemplate().getId();
			if (!visitedTemplateIds.add(templateId)) {
				continue;
			}
			for (TemplateField f : templateFieldRepository.findByTemplateId(templateId)) {
				if (f.getSchemaAttribute() == null || f.getSchemaAttribute().getId() == null) {
					continue;
				}
				if (f.getSemanticRole() == SemanticRole.TARGET_WORD) {
					targetAttrIds.add(f.getSchemaAttribute().getId());
				} else if (f.getSemanticRole() == SemanticRole.NATIVE_TRANSLATION) {
					translationAttrIds.add(f.getSchemaAttribute().getId());
				}
			}
		}

		List<Long> topicIds = topicTree.stream().map(Topic::getId).toList();
		List<Object[]> flatRows = topicValueRepository.findFlatValuesByTopicIds(topicIds);

		Map<Long, String> primaryWordByItem = new LinkedHashMap<>();
		Map<Long, String> fallbackWordByItem = new LinkedHashMap<>();
		Map<Long, String> translationByItem = new LinkedHashMap<>();

		for (Object[] row : flatRows) {
			Long itemId = (Long) row[0];
			Long attrId = (Long) row[1];
			String attrName = row[2] != null ? ((String) row[2]).toLowerCase(Locale.ROOT) : "";
			String val = row[3] != null ? ((String) row[3]).trim() : "";
			if (val.isEmpty()) {
				continue;
			}

			if (targetAttrIds.contains(attrId)) {
				primaryWordByItem.putIfAbsent(itemId, val);
			} else if (translationAttrIds.contains(attrId)) {
				translationByItem.putIfAbsent(itemId, val);
			} else {
				if (!primaryWordByItem.containsKey(itemId)
						&& (attrName.equals("word") || attrName.contains("word") || attrName.contains("term")
						|| attrName.contains("vocab") || attrName.contains("front"))) {
					primaryWordByItem.put(itemId, val);
				} else if (!translationByItem.containsKey(itemId)
						&& (attrName.contains("translation") || attrName.contains("meaning")
						|| attrName.contains("vietnamese") || attrName.equals("vi") || attrName.contains("back"))) {
					translationByItem.put(itemId, val);
				}
			}
			fallbackWordByItem.putIfAbsent(itemId, val);
		}

		List<StoryTopicWordsResponse.TopicWordItem> items = new ArrayList<>();
		Set<String> seenWords = new HashSet<>();
		for (Long itemId : fallbackWordByItem.keySet()) {
			String rawWord = primaryWordByItem.getOrDefault(itemId, fallbackWordByItem.get(itemId));
			if (rawWord == null) {
				continue;
			}
			String cleaned = rawWord.trim().replaceAll("\\s+", " ");
			if (cleaned.isEmpty() || cleaned.length() > 60) {
				continue;
			}
			String key = cleaned.toLowerCase(Locale.ROOT);
			if (!seenWords.add(key)) {
				continue;
			}
			items.add(StoryTopicWordsResponse.TopicWordItem.builder()
					.itemId(itemId)
					.word(cleaned)
					.translation(translationByItem.getOrDefault(itemId, ""))
					.build());
		}

		return items;
	}

	private List<String> resolveTargetWords(Long userId, GenerateStoryRequest request) {
		Topic systemTopic = request.getTopicId() != null ? requireSystemTopic(request.getTopicId()) : null;
		Map<String, String> deduped = new LinkedHashMap<>();

		if (request.getWords() != null) {
			for (String raw : request.getWords()) {
				addWord(deduped, raw);
			}
		}

		if (deduped.isEmpty() && systemTopic != null) {
			int desiredCount = request.getWordCount() != null
					? Math.min(storyProperties.getMaxWords(), Math.max(storyProperties.getMinWords(), request.getWordCount()))
					: DEFAULT_TOPIC_AUTO_PICK;

			List<StoryTopicWordsResponse.TopicWordItem> topicItems = new ArrayList<>(extractTopicWordItems(systemTopic));
			Collections.shuffle(topicItems);
			for (StoryTopicWordsResponse.TopicWordItem item : topicItems) {
				addWord(deduped, item.getWord());
				if (deduped.size() >= desiredCount) {
					break;
				}
			}
		}

		return new ArrayList<>(deduped.values());
	}

	private void collectTopics(Topic topic, List<Topic> acc) {
		if (topic == null || topic.getId() == null || acc.size() >= 100) {
			return;
		}
		acc.add(topic);
		if (topic.getChildren() != null) {
			for (Topic child : topic.getChildren()) {
				collectTopics(child, acc);
			}
		}
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

	private String buildSystemPrompt() {
		return """
				You are a creative Vietnamese-English "Truyện Chêm" (Macaronic storytelling) author.
				Write a lively, relatable, natural Vietnamese short story where EVERY target English vocabulary word is seamlessly woven directly into the Vietnamese sentences in English, wrapped in double square brackets like [[word]]. Never use **word**.
				Do NOT put Vietnamese translations in parentheses next to the English word — let the Vietnamese context make the meaning intuitive.
				Always return strictly valid JSON with NO markdown fences and NO extra text.
				""";
	}

	private String buildUserPrompt(List<String> words, StoryGenre genre) {
		String wordList = String.join(", ", words);
		int minSentences = Math.max(5, Math.min(9, (int) Math.ceil(words.size() * 0.55) + 2));
		int maxSentences = minSentences + 2;

		return """
				Create a Vietnamese-English "Truyện Chêm" story:
				- Setting / Vibe: %s
				- Target English words (%d words — MUST use ALL wrapped in [[word]]): %s
				- Length: %d to %d concise Vietnamese sentences across 2-3 paragraphs (2-3 target words may naturally share one sentence).

				Return strictly valid JSON:
				{
				  "title": "Tựa truyện tiếng Việt ngắn gọn, cuốn hút",
				  "targetWords": [
				    {"word": "exact word", "meaning": "nghĩa tiếng Việt ngắn gọn", "phonetic": "/IPA/", "partOfSpeech": "n./v./adj./adv."}
				  ],
				  "sentences": [
				    {"index": 1, "paragraph": 1, "text": "Câu tiếng Việt chêm [[word]] tự nhiên..."}
				  ]
				}
				""".formatted(genre.name(), words.size(), wordList, minSentences, maxSentences);
	}

	private ParsedStory parseStoryJson(String rawContent, List<String> fallbackWords) {
		String cleaned = stripCodeFence(rawContent);
		try {
			JsonNode root = objectMapper.readTree(cleaned);
			String title = root.path("title").asText("Truyện Chêm Từ Vựng").trim();
			if (title.isEmpty()) {
				title = "Truyện Chêm Từ Vựng";
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
					String rawText = n.path("text").asText("").trim();
					if (rawText.isEmpty()) {
						continue;
					}
					String normalizedText = normalizeMarkers(rawText, fallbackWords);
					int paragraph = n.path("paragraph").asInt(1);

					List<StoryDTO.StoryBlankDTO> blanks = new ArrayList<>();
					Matcher matcher = BLANK_PATTERN.matcher(normalizedText);
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
							.text(normalizedText)
							.blanks(blanks)
							.build());
					idx++;
				}
			}

			if (sentences.isEmpty()) {
				throw new IllegalStateException("AI model returned a story with no sentences.");
			}
			if (blankCounter == 0) {
				throw new IllegalStateException("AI model returned sentences without any [[targetWord]] markers.");
			}

			return new ParsedStory(title, targetWords, sentences, blankCounter);
		} catch (RuntimeException ex) {
			LOG.warn("Failed to parse LLM story JSON: {}", ex.getMessage());
			throw ex;
		} catch (Exception ex) {
			LOG.warn("Malformed LLM story JSON: {}", ex.getMessage());
			throw new IllegalStateException("AI returned malformed story format: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Converts accidental Markdown **word** into [[word]], and if a sentence still has no [[...]] markers,
	 * wraps bare occurrences of target words so highlighting & Cloze practice always work reliably.
	 */
	private String normalizeMarkers(String text, List<String> targetWords) {
		String normalized = BOLD_MARKER_PATTERN.matcher(text).replaceAll("[[$1]]");
		if (!BLANK_PATTERN.matcher(normalized).find() && targetWords != null) {
			for (String w : targetWords) {
				if (w == null || w.isBlank()) {
					continue;
				}
				Pattern bareWord = Pattern.compile("(?<!\\[)(?i)\\b(" + Pattern.quote(w.trim()) + ")\\b(?!\\])");
				normalized = bareWord.matcher(normalized).replaceFirst("[[$1]]");
			}
		}
		return normalized;
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
