package vn.ptit.snapvocab.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.service.WordService;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @GetMapping
    public ResponseEntity<LookupResultDTO> getWord(@RequestParam String englishWord) {
        LookupResultDTO result = wordService.getWordByEnglish(englishWord);
        return ResponseEntity.ok(result);
    }
}
