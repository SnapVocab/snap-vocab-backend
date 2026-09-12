package vn.ptit.snapvocab.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.word.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.word.LookupResultDTO;
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
}
