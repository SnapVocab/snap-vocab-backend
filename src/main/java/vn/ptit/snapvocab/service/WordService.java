package vn.ptit.snapvocab.service;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.common.Meta;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.word.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.word.LookupResultDTO;
import vn.ptit.snapvocab.domain.dto.word.WordSuggestDTO;
import vn.ptit.snapvocab.domain.mapper.LookupResultMapper;
import vn.ptit.snapvocab.repository.WordRepository;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

@Service
@Transactional
@RequiredArgsConstructor
public class WordService {

	private final Logger LOG = LoggerFactory.getLogger(WordService.class);

	private final WordRepository wordRepository;
	private final LookupResultMapper lookupResultMapper;

	public LookupResultDTO getWordByEnglish(String englishWord) {
		LOG.info("Fetching word by english text: {}", englishWord);
		Word word = wordRepository.findByWordAndLangCodeEn(englishWord)
				.orElseThrow(() -> new ResourceNotFoundException("Word not found: " + englishWord));
		return lookupResultMapper.toLookupResult(word);
	}

	public List<LookupResultDTO> getWordsByEnglish(BatchLookupRequest batchLookupRequest) {
		return wordRepository.findWordsByLangCodeEn(batchLookupRequest.words()).stream()
				.map(this::convertToLookupResult)
				.toList();
	}

	private LookupResultDTO convertToLookupResult(Word word) {
		return lookupResultMapper.toLookupResult(word);
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
				.map(lookupResultMapper::toLookupResult)
				.orElseThrow(() -> new ResourceNotFoundException("Word not found: " + id));
	}
}
