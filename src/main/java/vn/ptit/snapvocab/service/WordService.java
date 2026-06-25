package vn.ptit.snapvocab.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.Word;
import vn.ptit.snapvocab.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.domain.dto.PronunciationLookupDTO;
import vn.ptit.snapvocab.domain.dto.RelationLookupDTO;
import vn.ptit.snapvocab.domain.dto.TranslationLookupDTO;
import vn.ptit.snapvocab.domain.enumeration.CodeLabelMapper;
import vn.ptit.snapvocab.domain.enumeration.RelationLabel;
import vn.ptit.snapvocab.domain.mapper.MeaningMapper;
import vn.ptit.snapvocab.repository.WordRepository;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

@Service
@Transactional
@RequiredArgsConstructor
public class WordService {

    private final Logger LOG = LoggerFactory.getLogger(WordService.class);

    private final WordRepository wordRepository;
    private final MeaningMapper meaningMapper;

    public LookupResultDTO getWordByEnglish(String englishWord) {
        LOG.info("Fetching word by english text: {}", englishWord);
        Word word = wordRepository.findByWordAndLangCodeEn(englishWord)
                .orElseThrow(() -> new ResourceNotFoundException("Word not found: " + englishWord));

        List<PronunciationLookupDTO> pronunciations = word.getPronunciations().stream()
                .map(p -> new PronunciationLookupDTO(p.getIpa(), p.getRegion()))
                .toList();

        List<TranslationLookupDTO> translations = word.getTranslations().stream()
                .filter(t -> "vi".equals(t.getLangCode()))
                .map(t -> new TranslationLookupDTO(t.getLangCode(), t.getTranslation()))
                .toList();

        List<RelationLookupDTO> relations = word.getWordRelations().stream()
                .map(r -> new RelationLookupDTO(r.getRelatedWord(), CodeLabelMapper.fromCode(r.getRelationType(), RelationLabel.class)))
                .toList();

        return LookupResultDTO.builder()
                .exists(true)
                .word(word.getWord())
                .langCode("vi")
                .langName("Tiếng Việt")
                .pronunciations(pronunciations)
                .meanings(word.getWordDefinitions().stream().map(meaningMapper::toDto).toList())
                .translations(translations)
                .relations(relations)
                .build();
    }
}
