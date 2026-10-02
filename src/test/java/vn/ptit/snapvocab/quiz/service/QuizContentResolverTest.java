package vn.ptit.snapvocab.quiz.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemValueRow;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver.ItemContent;
import vn.ptit.snapvocab.quiz.exception.QuizException;

@ExtendWith(MockitoExtension.class)
class QuizContentResolverTest {

	private static final long WORD = 1L;
	private static final long MEANING = 2L;
	private static final long MAIN_TRANSLATION = 3L;
	private static final long EXAMPLE_TRANSLATION = 4L; // same name "translation", different group
	private static final long EXAMPLE_CONTENT = 5L;
	private static final long MEANING_EXAMPLE = 6L;

	@Mock
	private TemplateFieldRepository templateFieldRepository;
	@Mock
	private TopicItemAttributeValueRepository valueRepository;

	private QuizContentResolver resolver;
	private Topic topic;

	@BeforeEach
	void setUp() {
		resolver = new QuizContentResolver(templateFieldRepository, valueRepository);
		Template template = Template.builder().id(10L).build();
		topic = Topic.builder().id(7L).activeTemplate(template).build();
	}

	private static TemplateField field(long attributeId, String attributeName, SemanticRole role, boolean hidden) {
		SchemaAttribute attribute = SchemaAttribute.builder().id(attributeId).name(attributeName).build();
		return TemplateField.builder().schemaAttribute(attribute).semanticRole(role).hidden(hidden).build();
	}

	/** Mirrors DEFAULT_ENGLISH after the migration: visible fields plus the two hidden bindings. */
	private List<TemplateField> defaultEnglishFields() {
		return List.of(
				field(WORD, "word", SemanticRole.TARGET_WORD, false),
				field(MEANING, "meaning", SemanticRole.DEFINITION, false),
				field(MEANING_EXAMPLE, "example", SemanticRole.EXAMPLE_SENTENCE, false),
				field(EXAMPLE_TRANSLATION, "translation", null, false),
				field(MAIN_TRANSLATION, "translation", SemanticRole.NATIVE_TRANSLATION, true),
				field(EXAMPLE_CONTENT, "content", SemanticRole.EXAMPLE_SENTENCE, true));
	}

	@Test
	void resolvesNativeTranslationThroughHiddenBindingById() {
		when(templateFieldRepository.findByTemplateId(10L)).thenReturn(defaultEnglishFields());
		when(valueRepository.findItemValues(eq(7L), anyCollection())).thenAnswer(inv -> {
			assertThat((java.util.Collection<Long>) inv.getArgument(1))
					.containsExactlyInAnyOrder(WORD, MAIN_TRANSLATION);
			return List.of(
					new TopicItemValueRow(100L, WORD, "abandon"),
					new TopicItemValueRow(100L, MAIN_TRANSLATION, " từ bỏ "));
		});

		List<ItemContent> items = resolver.resolve(topic, QuizMode.MCQ);

		assertThat(items).containsExactly(new ItemContent(100L, "abandon", "từ bỏ", List.of()));
	}

	@Test
	void fillBlankCollectsExamplesFromEveryExampleBinding() {
		when(templateFieldRepository.findByTemplateId(10L)).thenReturn(defaultEnglishFields());
		when(valueRepository.findItemValues(eq(7L), anyCollection())).thenReturn(List.of(
				new TopicItemValueRow(100L, WORD, "abandon"),
				new TopicItemValueRow(100L, EXAMPLE_CONTENT, "They abandon ship."),
				new TopicItemValueRow(100L, EXAMPLE_CONTENT, "   "),
				new TopicItemValueRow(100L, MEANING_EXAMPLE, "Never abandon hope.")));

		ItemContent item = resolver.resolve(topic, QuizMode.FILL_BLANK).get(0);

		assertThat(item.exampleSentences()).containsExactly("They abandon ship.", "Never abandon hope.");
		assertThat(item.nativeTranslation()).isNull();
	}

	@Test
	void noFallbackFromNativeTranslationToDefinition() {
		when(templateFieldRepository.findByTemplateId(10L)).thenReturn(List.of(
				field(WORD, "word", SemanticRole.TARGET_WORD, false),
				field(MEANING, "meaning", SemanticRole.DEFINITION, false)));

		assertThatThrownBy(() -> resolver.resolve(topic, QuizMode.MATCHING))
				.isInstanceOf(QuizException.class)
				.extracting(e -> ((QuizException) e).getCode())
				.isEqualTo(QuizErrorCode.SEMANTIC_ROLE_NOT_BOUND);
	}

	@Test
	void singletonRoleBoundTwiceIsAmbiguous() {
		when(templateFieldRepository.findByTemplateId(10L)).thenReturn(List.of(
				field(WORD, "word", SemanticRole.TARGET_WORD, false),
				field(99L, "phonetic", SemanticRole.TARGET_WORD, false),
				field(MAIN_TRANSLATION, "translation", SemanticRole.NATIVE_TRANSLATION, true)));

		assertThatThrownBy(() -> resolver.resolve(topic, QuizMode.MCQ))
				.extracting(e -> ((QuizException) e).getCode())
				.isEqualTo(QuizErrorCode.SEMANTIC_ROLE_AMBIGUOUS);
	}

	@Test
	void sameAttributeBoundTwiceIsNotAmbiguous() {
		when(templateFieldRepository.findByTemplateId(10L)).thenReturn(List.of(
				field(WORD, "word", SemanticRole.TARGET_WORD, false),
				field(WORD, "word", SemanticRole.TARGET_WORD, true),
				field(MAIN_TRANSLATION, "translation", SemanticRole.NATIVE_TRANSLATION, true)));
		when(valueRepository.findItemValues(eq(7L), anyCollection())).thenReturn(List.of());

		assertThat(resolver.resolve(topic, QuizMode.MCQ)).isEmpty();
	}

	@Test
	void itemWithTwoDistinctTargetValuesHasNoTargetWord() {
		when(templateFieldRepository.findByTemplateId(10L)).thenReturn(defaultEnglishFields());
		when(valueRepository.findItemValues(eq(7L), anyCollection())).thenReturn(List.of(
				new TopicItemValueRow(100L, WORD, "run"),
				new TopicItemValueRow(100L, WORD, "sprint"),
				new TopicItemValueRow(100L, MAIN_TRANSLATION, "chạy")));

		assertThat(resolver.resolve(topic, QuizMode.MCQ).get(0).targetWord()).isNull();
	}

	@Test
	void topicWithoutActiveTemplateIsRejected() {
		Topic bare = Topic.builder().id(8L).build();
		assertThatThrownBy(() -> resolver.resolve(bare, QuizMode.MCQ))
				.extracting(e -> ((QuizException) e).getCode())
				.isEqualTo(QuizErrorCode.TOPIC_TEMPLATE_NOT_CONFIGURED);
	}

	@Test
	void requiredRolesNeverIncludeAudioImageOrDefinition() {
		for (QuizMode mode : QuizMode.values()) {
			assertThat(QuizContentResolver.requiredRoles(mode))
					.doesNotContain(SemanticRole.AUDIO, SemanticRole.IMAGE, SemanticRole.DEFINITION)
					.contains(SemanticRole.TARGET_WORD);
		}
		assertThat(QuizContentResolver.requiredRoles(QuizMode.FILL_BLANK))
				.isEqualTo(Set.of(SemanticRole.TARGET_WORD, SemanticRole.EXAMPLE_SENTENCE));
	}
}

