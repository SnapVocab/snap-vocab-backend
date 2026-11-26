package vn.ptit.snapvocab.dictionary.service;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.dictionary.domain.dto.BatchLookupRequest;
import vn.ptit.snapvocab.dictionary.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.MyWordDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO;
import vn.ptit.snapvocab.dictionary.domain.mapper.LookupResultMapper;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.dictionary.repository.UserWordRepository;
import vn.ptit.snapvocab.user.service.UserService;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
@Transactional
@RequiredArgsConstructor
public class WordService {

	private final Logger LOG = LoggerFactory.getLogger(WordService.class);

	private final WordRepository wordRepository;
	private final LookupResultMapper lookupResultMapper;
	private final UserWordRepository userWordRepository;
	private final UserService userService;
	private final TopicItemAttributeValueRepository topicItemAttributeValueRepository;

	public LookupResultDTO getWordByEnglish(String englishWord) {
		LOG.info("Fetching word by english text: {}", englishWord);
		Word word = wordRepository.findByWordAndLangCodeEn(englishWord)
				.orElseThrow(() -> new ResourceNotFoundException("Word not found: " + englishWord));
		return convertToLookupResult(word);
	}

	public List<LookupResultDTO> getWordsByEnglish(BatchLookupRequest batchLookupRequest) {
		List<Word> words = wordRepository.findWordsByLangCodeEn(batchLookupRequest.words());
		if (words.isEmpty()) {
			return List.of();
		}
		Long userId = currentUserId();
		Set<Long> savedIds = userWordRepository.findSavedWordIds(userId,
				words.stream().map(Word::getId).toList());
		return words.stream()
				.map(word -> {
					LookupResultDTO result = lookupResultMapper.toLookupResult(word);
					result.setInMyVocabulary(savedIds.contains(word.getId()));
					return result;
				})
				.toList();
	}

	private LookupResultDTO convertToLookupResult(Word word) {
		LookupResultDTO result = lookupResultMapper.toLookupResult(word);
		result.setInMyVocabulary(userWordRepository.existsByUser_IdAndWord_Id(currentUserId(), word.getId()));
		return result;
	}

	public PaginationObject<List<WordSuggestDTO>> getSuggestions(@Valid String q, Pageable pageable) {
		Page<WordSuggestDTO> wordSuggestDTOPage = wordRepository.findWordSuggestByLangCodeEn(q,
				pageable);
		PaginationObject<List<WordSuggestDTO>> paginationObject = new PaginationObject<>();
		Meta meta = Meta.builder()
				.size((long) pageable.getPageSize())
				.page((long) pageable.getPageNumber())
				.total(wordSuggestDTOPage.getTotalElements())
				.pages((long) wordSuggestDTOPage.getTotalPages())
				.build();
		paginationObject.setMeta(meta);
		paginationObject.setData(wordSuggestDTOPage.getContent());
		return paginationObject;
	}

	public LookupResultDTO getWordById(Long id) {
		return wordRepository.findWordById(id)
				.map(this::convertToLookupResult)
				.orElseThrow(() -> new ResourceNotFoundException("Word not found: " + id));
	}

	public void addMyWord(Long wordId) {
		Long userId = currentUserId();
		if (!wordRepository.existsById(wordId)) {
			throw new ResourceNotFoundException("Word not found: " + wordId);
		}
		userWordRepository.addWord(userId, wordId);
	}

	public void removeMyWord(Long wordId) {
		userWordRepository.removeWord(currentUserId(), wordId);
	}

	static final String MEANING_LANG = "vi";

	/**
	 * "Từ của tôi", newest first. Each word carries its meaning and the learner's topics that already hold it, in three
	 * batched queries per page.
	 */
	@Transactional(readOnly = true)
	public PaginationObject<List<MyWordDTO>> getMyWords(Pageable pageable) {
		Long userId = currentUserId();
		Page<WordSuggestDTO> page = userWordRepository.findMyWords(userId, pageable);
		List<WordSuggestDTO> words = page.getContent();
		Map<Long, String> meanings = meanings(words.stream().map(WordSuggestDTO::getId).toList());
		Map<String, List<MyWordDTO.Topic>> topics = topicsHolding(userId, words);

		PaginationObject<List<MyWordDTO>> result = new PaginationObject<>();
		result.setData(words.stream()
				.map(word -> new MyWordDTO(word.getId(), word.getWord(), meanings.get(word.getId()),
						topics.getOrDefault(normalize(word.getWord()), List.of())))
				.toList());
		result.setMeta(Meta.builder()
				.size((long) pageable.getPageSize())
				.page((long) pageable.getPageNumber())
				.total(page.getTotalElements())
				.pages((long) page.getTotalPages()).build());
		return result;
	}

	/** First translation, else first definition, in {@link #MEANING_LANG}. */
	private Map<Long, String> meanings(List<Long> wordIds) {
		Map<Long, String> meanings = new HashMap<>();
		if (wordIds.isEmpty()) {
			return meanings;
		}
		for (Object[] row : wordRepository.findTranslations(wordIds, MEANING_LANG)) {
			putFirstNonBlank(meanings, (Long) row[0], (String) row[1]);
		}
		List<Long> missing = wordIds.stream().filter(id -> !meanings.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			for (Object[] row : wordRepository.findDefinitions(missing, MEANING_LANG)) {
				putFirstNonBlank(meanings, (Long) row[0], (String) row[1]);
			}
		}
		return meanings;
	}

	private static void putFirstNonBlank(Map<Long, String> map, Long key, String value) {
		if (value != null && !value.isBlank()) {
			map.putIfAbsent(key, value.trim());
		}
	}

	private Map<String, List<MyWordDTO.Topic>> topicsHolding(Long userId, List<WordSuggestDTO> words) {
		List<String> keys = words.stream().map(word -> normalize(word.getWord())).filter(key -> !key.isEmpty())
				.distinct().toList();
		Map<String, List<MyWordDTO.Topic>> byWord = new HashMap<>();
		if (keys.isEmpty()) {
			return byWord;
		}
		for (Object[] row : topicItemAttributeValueRepository.findOwnTopicsContaining(userId, CollectionType.USER,
				SemanticRole.TARGET_WORD, keys)) {
			byWord.computeIfAbsent((String) row[0], k -> new ArrayList<>())
					.add(new MyWordDTO.Topic((Long) row[1], (String) row[2], (Long) row[3], (String) row[4]));
		}
		return byWord;
	}

	private static String normalize(String word) {
		return word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
	}

	private Long currentUserId() {
		return userService.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required for personal vocabulary"));
	}
}

