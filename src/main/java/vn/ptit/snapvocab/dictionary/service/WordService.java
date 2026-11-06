package vn.ptit.snapvocab.dictionary.service;

import jakarta.validation.Valid;
import java.util.List;
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
import vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO;
import vn.ptit.snapvocab.dictionary.domain.mapper.LookupResultMapper;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.dictionary.repository.UserWordRepository;
import vn.ptit.snapvocab.user.service.UserService;
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

	@Transactional(readOnly = true)
	public PaginationObject<List<WordSuggestDTO>> getMyWords(Pageable pageable) {
		Page<WordSuggestDTO> page = userWordRepository.findMyWords(currentUserId(), pageable);
		PaginationObject<List<WordSuggestDTO>> result = new PaginationObject<>();
		result.setData(page.getContent());
		result.setMeta(Meta.builder()
				.size((long) pageable.getPageSize())
				.page((long) pageable.getPageNumber())
				.total(page.getTotalElements())
				.pages((long) page.getTotalPages()).build());
		return result;
	}

	private Long currentUserId() {
		return userService.getCurrentUserId()
				.orElseThrow(() -> new AccessDeniedException("Authentication required for personal vocabulary"));
	}
}

