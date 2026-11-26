package vn.ptit.snapvocab.dictionary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.audit.service.AuditReason;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.dictionary.domain.Definition;
import vn.ptit.snapvocab.dictionary.domain.Pronunciation;
import vn.ptit.snapvocab.dictionary.domain.Translation;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.WordDefinition;
import vn.ptit.snapvocab.dictionary.domain.dto.AdminWordDTOs.DeletedWordDTO;
import vn.ptit.snapvocab.dictionary.domain.dto.AdminWordDTOs.MeaningInput;
import vn.ptit.snapvocab.dictionary.domain.dto.AdminWordDTOs.PronunciationInput;
import vn.ptit.snapvocab.dictionary.domain.dto.AdminWordDTOs.UpdateWordRequest;
import vn.ptit.snapvocab.dictionary.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.dictionary.domain.enumeration.Labeled;
import vn.ptit.snapvocab.dictionary.domain.enumeration.PosLabel;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.vocab.exception.ContentErrorCode;
import vn.ptit.snapvocab.vocab.exception.ContentException;

/**
 * Admin › Từ điển writes (FR-13.02): edit a word, soft-delete it, restore it. The word text itself never changes: topic
 * items, scans and learners' saved words find the word by it.
 */
@Service
@RequiredArgsConstructor
public class AdminWordService {

	private static final Set<String> POS_CODES = Arrays.stream(PosLabel.values()).map(Labeled::getCode)
			.collect(Collectors.toUnmodifiableSet());
	private static final int MAX_PAGE_SIZE = 100;

	private final WordRepository wordRepository;
	private final WordService wordService;
	private final AdminAuditService audit;
	private final ObjectMapper objectMapper;

	@PersistenceContext
	private EntityManager em;

	@Transactional
	public LookupResultDTO update(Long id, UpdateWordRequest request) {
		Word word = lock(id);
		if (word.getDeletedAt() != null) {
			throw new ContentException(ContentErrorCode.WORD_DELETED, "Word " + id + " is soft-deleted; restore it first");
		}
		if (request.version() != word.getVersion()) {
			throw ContentException.versionConflict("Word " + id, word.getVersion());
		}
		List<String> pronunciations = check(request);
		LookupResultDTO before = wordService.getWordById(id);

		List<AuditChange> changes = new ArrayList<>();
		addChange(changes, "pronunciations", before.getPronunciations().stream()
				.map(p -> label(p.getIpa(), p.getRegion())).toList(), pronunciations);
		addChange(changes, "translations", before.getTranslations().stream().map(t -> t.getTranslation()).toList(),
				request.translations().stream().map(String::strip).toList());
		Map<Long, WordDefinition> existing = em.createQuery("""
				SELECT wd FROM WordDefinition wd JOIN FETCH wd.definition WHERE wd.word.id = :id ORDER BY wd.id
				""", WordDefinition.class).setParameter("id", id).getResultStream()
				// Ordered like the lookup (id ASC): the audit compares lists, and a reorder is not an edit.
				.collect(Collectors.toMap(WordDefinition::getId, Function.identity(), (x, y) -> x, LinkedHashMap::new));
		addChange(changes, "meanings", existing.values().stream().map(AdminWordService::meaningLabel).toList(),
				request.meanings().stream().map(AdminWordService::meaningLabel).toList());
		if (changes.isEmpty()) {
			return before;
		}

		replacePronunciations(word, request.pronunciations());
		replaceTranslations(word, request.translations());
		replaceMeanings(word, request.meanings(), existing);
		word.setVersion(word.getVersion() + 1);
		em.flush();
		audit.record(AuditAction.WORD_UPDATED, target(word), changes, null, null, word.getVersion());
		em.clear();
		return wordService.getWordById(id);
	}

	/** Soft delete (FR-13 rule): the row and everything pointing at it stay; search, lookup and scan stop seeing it. */
	@Transactional
	public LookupResultDTO softDelete(Long id, String rawReason) {
		String reason = AuditReason.check(rawReason, false, AuditReason.DELETE, null);
		Word word = lock(id);
		if (word.getDeletedAt() != null) {
			throw new ContentException(ContentErrorCode.WORD_DELETED, "Word " + id + " is already soft-deleted");
		}
		word.setDeletedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
		word.setVersion(word.getVersion() + 1);
		em.flush();
		audit.record(AuditAction.WORD_DELETED, target(word),
				List.of(AuditDiff.change(objectMapper, "status", "ACTIVE", "DELETED")), reason, null, word.getVersion());
		return wordService.getWordById(id);
	}

	@Transactional
	public LookupResultDTO restore(Long id, String rawReason) {
		String reason = AuditReason.check(rawReason, false, AuditReason.DELETE, null);
		Word word = lock(id);
		if (word.getDeletedAt() == null) {
			throw new ContentException(ContentErrorCode.WORD_NOT_DELETED, "Word " + id + " is not deleted");
		}
		word.setDeletedAt(null);
		word.setVersion(word.getVersion() + 1);
		em.flush();
		audit.record(AuditAction.WORD_RESTORED, target(word),
				List.of(AuditDiff.change(objectMapper, "status", "DELETED", "ACTIVE")), reason, null, word.getVersion());
		return wordService.getWordById(id);
	}

	@Transactional(readOnly = true)
	public PaginationObject<List<DeletedWordDTO>> deleted(String q, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BadRequestAlertException("page must be >= 0 and size 1.." + MAX_PAGE_SIZE);
		}
		Page<Word> words = wordRepository.findDeleted(q == null ? "" : q.strip(), PageRequest.of(page, size));
		return PaginationObject.<List<DeletedWordDTO>>builder()
				.data(words.map(w -> new DeletedWordDTO(w.getId(), w.getWord(), w.getDeletedAt())).getContent())
				.meta(Meta.builder().page((long) page).size((long) size).total(words.getTotalElements())
						.pages((long) words.getTotalPages()).build())
				.build();
	}

	private Word lock(Long id) {
		return wordRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Word not found: " + id));
	}

	/** Rules Bean Validation cannot say; returns the IPA labels in body order. */
	private static List<String> check(UpdateWordRequest request) {
		List<String> pronunciations = new ArrayList<>();
		for (int i = 0; i < request.pronunciations().size(); i++) {
			PronunciationInput p = request.pronunciations().get(i);
			String label = label(p.ipa().strip(), blankToNull(p.region()));
			if (pronunciations.contains(label)) {
				// pronunciations has a unique key on (word, ipa, region)
				throw ContentException.invalid("pronunciations[" + i + "]", "Duplicate pronunciation " + label);
			}
			pronunciations.add(label);
		}
		Set<Long> ids = new HashSet<>();
		for (int i = 0; i < request.meanings().size(); i++) {
			MeaningInput m = request.meanings().get(i);
			String pos = blankToNull(m.pos());
			if (pos != null && !POS_CODES.contains(pos)) {
				throw ContentException.invalid("meanings[" + i + "].pos", "Unknown part of speech code " + pos);
			}
			if (m.id() != null && !ids.add(m.id())) {
				throw ContentException.invalid("meanings[" + i + "].id", "Meaning " + m.id() + " appears twice");
			}
		}
		return pronunciations;
	}

	private void replacePronunciations(Word word, List<PronunciationInput> input) {
		em.createQuery("DELETE FROM Pronunciation p WHERE p.word.id = :id").setParameter("id", word.getId())
				.executeUpdate();
		for (PronunciationInput p : input) {
			em.persist(Pronunciation.builder().word(word).ipa(p.ipa().strip()).region(blankToNull(p.region())).build());
		}
	}

	/** Vietnamese only: other languages come from the import and the editor does not show them. */
	private void replaceTranslations(Word word, List<String> input) {
		em.createQuery("DELETE FROM Translation t WHERE t.word.id = :id AND t.langCode = 'vi'")
				.setParameter("id", word.getId()).executeUpdate();
		for (String text : input) {
			Translation translation = new Translation();
			translation.setWord(word);
			translation.setLangCode("vi");
			translation.setTranslation(text.strip());
			em.persist(translation);
		}
	}

	/**
	 * A definition row can belong to many words (47k shared on prod): editing one in place would change every word
	 * that shares it, so a shared definition is copied first and only this word moves to the copy.
	 */
	private void replaceMeanings(Word word, List<MeaningInput> input, Map<Long, WordDefinition> existing) {
		Set<Long> kept = new HashSet<>();
		for (int i = 0; i < input.size(); i++) {
			MeaningInput m = input.get(i);
			String text = m.definition().strip();
			String pos = blankToNull(m.pos());
			String example = blankToNull(m.example());
			if (m.id() == null) {
				Definition definition = Definition.builder().definition(text).pos(pos)
						.definitionLang(m.definitionLang()).build();
				em.persist(definition);
				WordDefinition link = new WordDefinition();
				link.setWord(word);
				link.setDefinition(definition);
				link.setExample(example);
				em.persist(link);
				continue;
			}
			WordDefinition link = existing.get(m.id());
			if (link == null) {
				throw ContentException.invalid("meanings[" + i + "].id", "Meaning " + m.id() + " is not on this word");
			}
			kept.add(link.getId());
			Definition current = link.getDefinition();
			boolean samePos = Objects.equals(current.getPos(), pos);
			if (!samePos || !Objects.equals(current.getDefinition(), text)
					|| !Objects.equals(current.getDefinitionLang(), m.definitionLang())) {
				if (sharedWithOtherWords(current, link)) {
					Definition copy = Definition.builder().definition(text).pos(pos)
							.subPos(samePos ? current.getSubPos() : null).definitionLang(m.definitionLang())
							.links(current.getLinks()).build();
					em.persist(copy);
					link.setDefinition(copy);
				} else {
					current.setDefinition(text);
					current.setDefinitionLang(m.definitionLang());
					if (!samePos) {
						current.setPos(pos);
						current.setSubPos(null); // a sub-category of the old part of speech no longer applies
					}
				}
			}
			link.setExample(example);
		}
		for (WordDefinition link : existing.values()) {
			if (!kept.contains(link.getId())) {
				Definition definition = link.getDefinition();
				em.remove(link);
				em.flush();
				if (!sharedWithOtherWords(definition, null)) {
					em.remove(definition);
				}
			}
		}
	}

	private boolean sharedWithOtherWords(Definition definition, WordDefinition except) {
		return em.createQuery("""
				SELECT COUNT(wd) FROM WordDefinition wd WHERE wd.definition.id = :definitionId AND wd.id <> :except
				""", Long.class).setParameter("definitionId", definition.getId())
				.setParameter("except", except == null ? -1L : except.getId()).getSingleResult() > 0;
	}

	private void addChange(List<AuditChange> changes, String field, List<String> before, List<String> after) {
		if (!before.equals(after)) {
			changes.add(AuditDiff.change(objectMapper, field, before, after));
		}
	}

	private static String meaningLabel(WordDefinition wd) {
		Definition d = wd.getDefinition();
		return meaningLabel(d.getPos(), d.getDefinitionLang(), d.getDefinition(), wd.getExample());
	}

	private static String meaningLabel(MeaningInput m) {
		return meaningLabel(blankToNull(m.pos()), m.definitionLang(), m.definition().strip(), blankToNull(m.example()));
	}

	private static String meaningLabel(String pos, String lang, String definition, String example) {
		return (pos == null ? "-" : pos) + " · " + lang + " · " + definition + (example == null ? "" : " · " + example);
	}

	private static String label(String ipa, String region) {
		return region == null ? ipa : ipa + " (" + region + ")";
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	private static AuditTarget target(Word word) {
		return AuditTarget.of(AuditTargetType.WORD, word.getId(), word.getWord(), null);
	}
}
