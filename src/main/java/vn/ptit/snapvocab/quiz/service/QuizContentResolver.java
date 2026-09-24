package vn.ptit.snapvocab.quiz.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.domain.Template;
import vn.ptit.snapvocab.domain.TemplateField;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemValueRow;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.quiz.exception.QuizException;

@Component
public class QuizContentResolver {

	static final Set<SemanticRole> SINGLETON_ROLES = EnumSet.of(SemanticRole.TARGET_WORD,
			SemanticRole.NATIVE_TRANSLATION, SemanticRole.DEFINITION);

	public record ItemContent(Long topicItemId, String targetWord, String nativeTranslation,
			List<String> exampleSentences) {
	}

	private final TemplateFieldRepository templateFieldRepository;
	private final TopicItemAttributeValueRepository valueRepository;

	public QuizContentResolver(TemplateFieldRepository templateFieldRepository,
			TopicItemAttributeValueRepository valueRepository) {
		this.templateFieldRepository = templateFieldRepository;
		this.valueRepository = valueRepository;
	}

	static Set<SemanticRole> requiredRoles(QuizMode mode) {
		return switch (mode) {
			case MCQ, MATCHING -> EnumSet.of(SemanticRole.TARGET_WORD, SemanticRole.NATIVE_TRANSLATION);
			case FILL_BLANK -> EnumSet.of(SemanticRole.TARGET_WORD, SemanticRole.EXAMPLE_SENTENCE);
		};
	}

	public List<ItemContent> resolve(Topic topic, QuizMode mode) {
		Template template = topic.getActiveTemplate();
		if (template == null) {
			throw new QuizException(QuizErrorCode.TOPIC_TEMPLATE_NOT_CONFIGURED,
					"Topic " + topic.getId() + " has no active template");
		}

		Map<SemanticRole, Set<Long>> attributesByRole = bindings(
				templateFieldRepository.findByTemplateId(template.getId()), requiredRoles(mode));

		Map<Long, SemanticRole> roleByAttribute = new HashMap<>();
		attributesByRole.forEach((role, ids) -> ids.forEach(id -> roleByAttribute.put(id, role)));

		Map<Long, Map<SemanticRole, Set<String>>> valuesByItem = new LinkedHashMap<>();
		for (TopicItemValueRow row : valueRepository.findItemValues(topic.getId(), roleByAttribute.keySet())) {
			String value = row.value() == null ? "" : row.value().trim();
			if (value.isEmpty()) {
				continue;
			}
			valuesByItem.computeIfAbsent(row.topicItemId(), k -> new EnumMap<>(SemanticRole.class))
					.computeIfAbsent(roleByAttribute.get(row.attributeId()), k -> new LinkedHashSet<>())
					.add(value);
		}

		List<ItemContent> items = new ArrayList<>(valuesByItem.size());
		valuesByItem.forEach((itemId, values) -> items.add(new ItemContent(
				itemId,
				single(values.get(SemanticRole.TARGET_WORD)),
				single(values.get(SemanticRole.NATIVE_TRANSLATION)),
				List.copyOf(values.getOrDefault(SemanticRole.EXAMPLE_SENTENCE, Set.of())))));
		return items;
	}

	static Map<SemanticRole, Set<Long>> bindings(List<TemplateField> fields, Set<SemanticRole> required) {
		Map<SemanticRole, Set<Long>> result = new EnumMap<>(SemanticRole.class);
		for (TemplateField field : fields) {
			if (field.getSemanticRole() != null && required.contains(field.getSemanticRole())) {
				result.computeIfAbsent(field.getSemanticRole(), k -> new LinkedHashSet<>())
						.add(field.getSchemaAttribute().getId());
			}
		}
		for (SemanticRole role : required) {
			Set<Long> ids = result.get(role);
			if (ids == null || ids.isEmpty()) {
				throw new QuizException(QuizErrorCode.SEMANTIC_ROLE_NOT_BOUND,
						"Active template binds no field to " + role, Map.of("semanticRole", role));
			}
			if (SINGLETON_ROLES.contains(role) && ids.size() > 1) {
				throw new QuizException(QuizErrorCode.SEMANTIC_ROLE_AMBIGUOUS,
						"Active template binds " + role + " to " + ids.size() + " attributes",
						Map.of("semanticRole", role));
			}
		}
		return result;
	}

	private static String single(Set<String> values) {
		return values != null && values.size() == 1 ? values.iterator().next() : null;
	}
}
