package vn.ptit.snapvocab.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.word.BatchLookupRequest;
import vn.ptit.snapvocab.domain.dto.word.LookupResultDTO;
import vn.ptit.snapvocab.domain.dto.word.WordSuggestDTO;
import vn.ptit.snapvocab.service.WordService;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

	private final WordService wordService;

	@GetMapping("/lookup")
	public ResponseEntity<LookupResultDTO> getWord(@RequestParam(required = true) String q) {
		LookupResultDTO result = wordService.getWordByEnglish(q);
		return ResponseEntity.ok(result);
	}

	@PostMapping("/lookup")
	public ResponseEntity<List<LookupResultDTO>> getWords(
			@RequestBody @Valid BatchLookupRequest batchLookupRequest) {
		List<LookupResultDTO> results = wordService.getWordsByEnglish(batchLookupRequest);
		return ResponseEntity.ok(results);
	}

	@GetMapping("/suggest")
	public ResponseEntity<PaginationObject<List<WordSuggestDTO>>> getSuggestions(
			@RequestParam(required = true) String q, Pageable pageable) {
		return ResponseEntity.ok(wordService.getSuggestions(q, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<LookupResultDTO> getWordById(@PathVariable(required = true) Long id) {
		return ResponseEntity.ok(wordService.getWordById(id));
	}
}
