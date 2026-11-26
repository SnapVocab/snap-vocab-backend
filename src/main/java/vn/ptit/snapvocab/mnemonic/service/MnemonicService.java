package vn.ptit.snapvocab.mnemonic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.mnemonic.domain.MnemonicType;
import vn.ptit.snapvocab.mnemonic.domain.WordMnemonic;
import vn.ptit.snapvocab.mnemonic.dto.LeechListDTO;
import vn.ptit.snapvocab.mnemonic.dto.LeechWordDTO;
import vn.ptit.snapvocab.mnemonic.dto.MnemonicDTO;
import vn.ptit.snapvocab.mnemonic.dto.MnemonicResultDTO;
import vn.ptit.snapvocab.mnemonic.exception.MnemonicErrorCode;
import vn.ptit.snapvocab.mnemonic.exception.MnemonicException;
import vn.ptit.snapvocab.mnemonic.repository.WordMnemonicRepository;
import vn.ptit.snapvocab.story.service.LlmGatewayService;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

/**
 * AI mnemonics for leech words: once a card has been forgotten {@link #MIN_FORGET_COUNT} times the learner may ask
 * the configured LLM chain for an Anh–Việt association, kept per learner (see {@link WordMnemonic}).
 */
@Service
@RequiredArgsConstructor
public class MnemonicService {

	/** "Quên" at least this many times makes a word a leech (product decision 2026-11-26). */
	public static final int MIN_FORGET_COUNT = 2;
	static final int MAX_CONTENT_LENGTH = 600;
	static final int MAX_KEYWORD_LENGTH = 120;
	static final int MAX_LEECH_LIST_SIZE = 200;
	private static final int GENERATION_ATTEMPTS = 2;
	private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Logger LOG = LoggerFactory.getLogger(MnemonicService.class);

	static final String SYSTEM_PROMPT = """
			Bạn là trợ lý giúp người Việt nhớ từ vựng tiếng Anh mà họ hay quên.
			Hãy tạo MỘT mẹo nhớ ngắn, vui, dễ hình dung, nối từ tiếng Anh với nghĩa tiếng Việt của nó.
			Chọn MỘT kiểu hợp với từ nhất (không mặc định chọn SOUND_ALIKE):
			- SOUND_ALIKE: cách đọc từ nghe giống một cụm tiếng Việt CÓ NGHĨA THẬT, và chính cụm đó gợi ra nghĩa của từ. \
			Ví dụ tốt: "hesitate" (do dự) nghe như "hét đi Tết": cứ hét "đi Tết hay không" mà mãi do dự; \
			"conscientious" (tận tâm) nghe như "con sen tận tâm"; "deteriorate" (xấu đi) nghe như "đi tới rồi hư".
			- WORDPLAY: tách từ/cụm thành các phần tiếng Anh quen thuộc mà nghĩa ghép lại dẫn tới nghĩa của từ. \
			Ví dụ tốt: "nevertheless" = never + the + less: chẳng bao giờ bớt đi, tuy nhiên vẫn vậy; \
			"toothpaste" = tooth (răng) + paste (hồ): hồ cho răng. Mỗi phần phải là từ hoặc tiền tố/hậu tố tiếng Anh \
			có thật với nghĩa thật; KHÔNG tách thành chữ cái hay mảnh vô nghĩa (như "c + up", "d + esk", "u + big + quit").
			Từ ngắn, đơn giản, chỉ đồ vật quen thuộc thì thường hợp STORY hơn.
			- STORY: một cảnh ngắn, sinh động, đời thường, trong đó từ tiếng Anh xuất hiện đúng nghĩa. \
			Ví dụ tốt: "break the ice" (phá vỡ sự ngượng ngùng): cả phòng im như băng, anh ấy kể chuyện cười \
			để break the ice.
			Nếu không tìm được cụm tiếng Việt nghe giống mà có nghĩa thật thì dùng WORDPLAY hoặc STORY. \
			KHÔNG dùng SOUND_ALIKE kiểu phiên âm từng âm tiết vô nghĩa (như "tiu-bờ", "sờ-cru-ti-nai", "mau-s").
			Quy tắc:
			- Viết bằng tiếng Việt có dấu đầy đủ, tối đa 2 câu (dưới 60 từ); phải nhắc nguyên văn từ tiếng Anh \
			và nghĩa tiếng Việt. Nghĩa trên thẻ có thể bị gõ thiếu dấu: hãy viết lại cho có dấu.
			- Dùng đúng nghĩa trên thẻ (nếu thẻ có nhiều nghĩa thì chọn nghĩa đầu). Không đổi nghĩa, không bịa nghĩa \
			mới, không gán cho móc nhớ một nghĩa tiếng Anh khác. Mẹo phải hợp lý: người đọc thấy ngay vì sao móc nhớ \
			dẫn tới nghĩa.
			- Không nội dung thô tục, bạo lực hay nhạy cảm.
			- "keyword" là cụm tiếng Việt (hoặc phép tách từ) làm móc nhớ, ngắn gọn (có thể để trống).
			Chỉ trả về JSON hợp lệ, không markdown, đúng dạng:
			{"type":"SOUND_ALIKE|WORDPLAY|STORY","keyword":"...","mnemonic":"..."}
			""";

	private final StudyQueueRepository studyQueueRepository;
	private final WordMnemonicRepository mnemonicRepository;
	private final UserRepository userRepository;
	private final WordFactsReader wordFactsReader;
	private final LlmGatewayService llmGateway;
	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	@Value("${snapvocab.mnemonic.daily-limit:10}")
	private int dailyLimit = 10;

	/** Creates the word's mnemonic, or replaces it ("Tạo lại"). Costs one of today's generations unless the AI fails. */
	@Transactional
	public MnemonicResultDTO generate(Long userId, Long topicItemId) {
		FsrsRecord record = studyQueueRepository.findByUser_IdAndTopicItem_Id(userId, topicItemId)
				.orElseThrow(() -> new MnemonicException(MnemonicErrorCode.ITEM_NOT_FOUND,
						"No review of item " + topicItemId + " for user " + userId));
		int forgetCount = record.getForgetCount() == null ? 0 : record.getForgetCount();
		if (forgetCount < MIN_FORGET_COUNT) {
			throw new MnemonicException(MnemonicErrorCode.NOT_A_LEECH,
					"Item " + topicItemId + " forgotten " + forgetCount + " times");
		}
		WordFacts facts = wordFactsReader.read(List.of(topicItemId)).get(topicItemId);
		if (facts == null || !facts.hasWord()) {
			throw new MnemonicException(MnemonicErrorCode.WORD_MISSING, "Item " + topicItemId + " has no word");
		}

		RAtomicLong used = quotaCounter(userId);
		long afterThis = used.incrementAndGet();
		used.expire(Duration.ofDays(2));
		if (afterThis > dailyLimit) {
			used.decrementAndGet();
			throw new MnemonicException(MnemonicErrorCode.MNEMONIC_QUOTA_EXCEEDED, "Daily mnemonic limit reached",
					Map.of("dailyLimit", dailyLimit, "remainingToday", 0), null);
		}

		Optional<WordMnemonic> existing = mnemonicRepository.findByUserIdAndTopicItemId(userId, topicItemId);
		LlmGatewayService.ValidatedGeneration<ParsedMnemonic> generation;
		try {
			generation = generateWithRetry(userPrompt(facts, forgetCount, existing.orElse(null)), facts.word());
		}
		catch (RuntimeException ex) {
			// Not the learner's fault: give the generation back.
			used.decrementAndGet();
			LOG.warn("Mnemonic generation failed for user {} item {}: {}", userId, topicItemId, ex.getMessage());
			throw new MnemonicException(MnemonicErrorCode.AI_UNAVAILABLE, ex.getMessage(), null, ex);
		}

		ParsedMnemonic parsed = generation.parsed();
		WordMnemonic mnemonic = existing.orElseGet(() -> {
					WordMnemonic created = new WordMnemonic();
					created.setUser(userRepository.getReferenceById(userId));
					created.setTopicItem(record.getTopicItem());
					return created;
				});
		mnemonic.setType(parsed.type());
		mnemonic.setKeyword(parsed.keyword());
		mnemonic.setContent(parsed.content());
		mnemonic.setProviderName(generation.providerName());
		mnemonic.setModelName(generation.modelName());
		WordMnemonic saved = mnemonicRepository.saveAndFlush(mnemonic);
		LOG.info("Mnemonic for user {} item {} via {} / {}", userId, topicItemId, generation.providerName(),
				generation.modelName());
		return new MnemonicResultDTO(MnemonicDTO.of(saved), dailyLimit, remaining(afterThis));
	}

	/**
	 * Often only one provider is configured, so a single unusable answer (e.g. the word left out) would fail the
	 * learner; the model usually gets it right on a second try.
	 */
	private LlmGatewayService.ValidatedGeneration<ParsedMnemonic> generateWithRetry(String userPrompt, String word) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= GENERATION_ATTEMPTS; attempt++) {
			try {
				return llmGateway.generateAndParse(SYSTEM_PROMPT, userPrompt, raw -> parse(raw, word));
			}
			catch (RuntimeException ex) {
				last = ex;
				LOG.info("Mnemonic attempt {} failed: {}", attempt, ex.getMessage());
			}
		}
		throw last;
	}

	@Transactional
	public void delete(Long userId, Long topicItemId) {
		mnemonicRepository.deleteByUserIdAndTopicItemId(userId, topicItemId);
	}

	/** "Từ hay quên": most forgotten first, with their mnemonics and today's remaining generations. */
	@Transactional(readOnly = true)
	public LeechListDTO leeches(Long userId, int limit) {
		List<FsrsRecord> records = studyQueueRepository.findLeeches(userId, MIN_FORGET_COUNT, CardState.SUSPENDED,
				PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LEECH_LIST_SIZE))));
		List<Long> itemIds = records.stream().map(r -> r.getTopicItem().getId()).toList();
		Map<Long, WordFacts> facts = wordFactsReader.read(itemIds);
		Map<Long, MnemonicDTO> mnemonics = mnemonicsOf(userId, itemIds);
		List<LeechWordDTO> items = records.stream()
				.map(r -> {
					Long id = r.getTopicItem().getId();
					WordFacts f = facts.getOrDefault(id, new WordFacts(null, null, null, null, null));
					return new LeechWordDTO(id, f.word(), f.meaning() != null ? f.meaning() : f.definition(),
							r.getForgetCount(), mnemonics.get(id));
				})
				.filter(item -> item.word() != null)
				.toList();
		long total = studyQueueRepository.countLeeches(userId, MIN_FORGET_COUNT, CardState.SUSPENDED);
		return new LeechListDTO(items, total, MIN_FORGET_COUNT, dailyLimit, remaining(quotaCounter(userId).get()));
	}

	/** The learner's mnemonics for cards shown in a study session. */
	@Transactional(readOnly = true)
	public Map<Long, MnemonicDTO> mnemonicsOf(Long userId, List<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return mnemonicRepository.findByUserIdAndTopicItemIds(userId, itemIds).stream()
				.map(MnemonicDTO::of)
				.collect(Collectors.toMap(MnemonicDTO::topicItemId, Function.identity(), (a, b) -> a));
	}

	/** {@code previous} is the mnemonic being replaced ("Tạo lại"): the learner wants a different idea, not a rewording. */
	static String userPrompt(WordFacts facts, int forgetCount, WordMnemonic previous) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Từ tiếng Anh: ").append(facts.word()).append('\n');
		if (facts.phonetic() != null) {
			prompt.append("Phiên âm: ").append(facts.phonetic()).append('\n');
		}
		if (facts.meaning() != null) {
			prompt.append("Nghĩa tiếng Việt: ").append(facts.meaning()).append('\n');
		}
		if (facts.definition() != null) {
			prompt.append("Định nghĩa: ").append(facts.definition()).append('\n');
		}
		if (facts.example() != null) {
			prompt.append("Ví dụ: ").append(facts.example()).append('\n');
		}
		prompt.append("Người học đã quên từ này ").append(forgetCount).append(" lần.");
		if (previous != null && previous.getContent() != null) {
			prompt.append("\nMẹo cũ người học chưa ưng: \"").append(previous.getContent()).append('"');
			if (previous.getKeyword() != null && !previous.getKeyword().isBlank()) {
				prompt.append(" (móc nhớ \"").append(previous.getKeyword()).append("\")");
			}
			prompt.append(".\nHãy tạo mẹo KHÁC HẲN: không dùng lại móc nhớ hay ý tưởng đó");
			if (previous.getType() != null) {
				prompt.append(", ưu tiên kiểu khác ").append(previous.getType().name());
			}
			prompt.append('.');
		}
		return prompt.toString();
	}

	/** Like {@link #parse(String)}, and the text must name the word itself, or the learner can't tell what it is for. */
	ParsedMnemonic parse(String raw, String word) {
		ParsedMnemonic parsed = parse(raw);
		if (word != null && !parsed.content().toLowerCase(Locale.ROOT).contains(word.trim().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Mnemonic does not mention the word");
		}
		return parsed;
	}

	/** Runs inside the provider loop: an unusable answer moves on to the next provider. */
	ParsedMnemonic parse(String raw) {
		try {
			JsonNode root = objectMapper.readTree(stripToJson(raw));
			String content = text(root, "mnemonic");
			if (content == null) {
				throw new IllegalArgumentException("Mnemonic is empty");
			}
			if (content.length() > MAX_CONTENT_LENGTH) {
				throw new IllegalArgumentException("Mnemonic is too long: " + content.length());
			}
			MnemonicType type;
			try {
				type = MnemonicType.valueOf(String.valueOf(text(root, "type")).toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ex) {
				type = MnemonicType.STORY;
			}
			String keyword = text(root, "keyword");
			if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
				keyword = null;
			}
			return new ParsedMnemonic(type, keyword, content);
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new IllegalArgumentException("Mnemonic is not JSON", ex);
		}
	}

	private static String text(JsonNode root, String field) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		String value = node.asText().trim();
		return value.isEmpty() ? null : value;
	}

	private static String stripToJson(String raw) {
		if (raw == null) {
			return "";
		}
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		return start >= 0 && end > start ? raw.substring(start, end + 1) : raw.trim();
	}

	private RAtomicLong quotaCounter(Long userId) {
		return redissonClient.getAtomicLong("mnemonic:quota:" + userId + ":" + LocalDate.now(STUDY_ZONE));
	}

	private int remaining(long used) {
		return (int) Math.max(0, dailyLimit - used);
	}

	record ParsedMnemonic(MnemonicType type, String keyword, String content) {
	}
}
