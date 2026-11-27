package vn.ptit.snapvocab.mnemonic.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;

/**
 * Reads word, meaning, definition and example of cards in one or two queries. Roles come from each topic's active
 * template; a card whose template binds no word falls back to attribute names ("word", "translation", ...), like the
 * story picker does.
 */
@Component
@RequiredArgsConstructor
public class WordFactsReader {

	private static final List<SemanticRole> ROLES = List.of(SemanticRole.TARGET_WORD, SemanticRole.PHONETIC,
			SemanticRole.NATIVE_TRANSLATION, SemanticRole.DEFINITION, SemanticRole.EXAMPLE_SENTENCE);

	private final TopicItemAttributeValueRepository valueRepository;

	public Map<Long, WordFacts> read(Collection<Long> itemIds) {
		Map<Long, Map<SemanticRole, String>> byRole = new HashMap<>();
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		for (Object[] row : valueRepository.findRoleValuesOfItems(itemIds, ROLES)) {
			String value = clean(row[2]);
			if (value != null) {
				byRole.computeIfAbsent((Long) row[0], id -> new HashMap<>()).putIfAbsent((SemanticRole) row[1], value);
			}
		}
		List<Long> withoutWord = itemIds.stream()
				.filter(id -> !byRole.getOrDefault(id, Map.of()).containsKey(SemanticRole.TARGET_WORD))
				.toList();
		if (!withoutWord.isEmpty()) {
			for (Object[] row : valueRepository.findFlatValuesByItemIds(withoutWord)) {
				String value = clean(row[2]);
				SemanticRole role = guessRole((String) row[1]);
				if (value != null && role != null) {
					byRole.computeIfAbsent((Long) row[0], id -> new HashMap<>()).putIfAbsent(role, value);
				}
			}
		}
		Map<Long, WordFacts> facts = new HashMap<>();
		for (Long id : itemIds) {
			Map<SemanticRole, String> values = byRole.getOrDefault(id, Map.of());
			facts.put(id, new WordFacts(values.get(SemanticRole.TARGET_WORD), values.get(SemanticRole.PHONETIC),
					values.get(SemanticRole.NATIVE_TRANSLATION), values.get(SemanticRole.DEFINITION),
					values.get(SemanticRole.EXAMPLE_SENTENCE)));
		}
		return facts;
	}

	static SemanticRole guessRole(String attributeName) {
		String name = attributeName == null ? "" : attributeName.toLowerCase(Locale.ROOT);
		if (name.equals("word") || name.contains("term") || name.contains("vocab")) {
			return SemanticRole.TARGET_WORD;
		}
		if (name.contains("phonetic") || name.equals("ipa")) {
			return SemanticRole.PHONETIC;
		}
		if (name.contains("translation") || name.equals("meaning") || name.contains("vietnamese")) {
			return SemanticRole.NATIVE_TRANSLATION;
		}
		if (name.contains("definition")) {
			return SemanticRole.DEFINITION;
		}
		if (name.contains("example")) {
			return SemanticRole.EXAMPLE_SENTENCE;
		}
		return null;
	}

	private static String clean(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString().trim().replaceAll("\s+", " ");
		return text.isEmpty() ? null : text;
	}
}
