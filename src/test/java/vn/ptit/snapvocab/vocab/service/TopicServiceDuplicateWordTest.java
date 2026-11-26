package vn.ptit.snapvocab.vocab.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemValueRow;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;

/** buss_mainflow.md BF-07 AF-07.1: the same word is not saved twice into one topic. */
@ExtendWith(MockitoExtension.class)
class TopicServiceDuplicateWordTest {

	private static final long WORD_ATTR = 101L;
	private static final long MEANING_ATTR = 102L;

	@Mock
	private TemplateFieldRepository templateFieldRepository;
	@Mock
	private TopicItemAttributeValueRepository topicItemAttributeValueRepository;

	@InjectMocks
	private TopicService topicService;

	private static Topic topic() {
		return Topic.builder().id(9L).activeTemplate(Template.builder().id(3L).build()).build();
	}

	private static SchemaAttribute attribute(long id) {
		return SchemaAttribute.builder().id(id).name("a" + id).build();
	}

	private static TopicItem item(String word) {
		TopicItemAttributeGroup group = TopicItemAttributeGroup.builder().build();
		group.getValues().add(TopicItemAttributeValue.builder().attribute(attribute(WORD_ATTR)).value(word).build());
		group.getValues().add(TopicItemAttributeValue.builder().attribute(attribute(MEANING_ATTR)).value("nghĩa").build());
		TopicItem item = TopicItem.builder().build();
		item.getGroupInstances().add(group);
		return item;
	}

	private void templateBindsTargetWord() {
		when(templateFieldRepository.findByTemplateId(3L)).thenReturn(List.of(
				TemplateField.builder().schemaAttribute(attribute(WORD_ATTR)).semanticRole(SemanticRole.TARGET_WORD).build(),
				TemplateField.builder().schemaAttribute(attribute(MEANING_ATTR))
						.semanticRole(SemanticRole.NATIVE_TRANSLATION).build()));
	}

	@Test
	void rejectsTheSameWordIgnoringCaseAndSpaces() {
		templateBindsTargetWord();
		when(topicItemAttributeValueRepository.findItemValues(List.of(9L), java.util.Set.of(WORD_ATTR)))
				.thenReturn(List.of(new TopicItemValueRow(1L, WORD_ATTR, "Apple")));

		assertThatThrownBy(() -> topicService.rejectDuplicateWord(topic(), item("  apple ")))
				.isInstanceOf(ResourceAlreadyExistException.class)
				.hasMessage(TopicService.WORD_ALREADY_IN_TOPIC);
	}

	@Test
	void acceptsANewWord() {
		templateBindsTargetWord();
		when(topicItemAttributeValueRepository.findItemValues(List.of(9L), java.util.Set.of(WORD_ATTR)))
				.thenReturn(List.of(new TopicItemValueRow(1L, WORD_ATTR, "apple")));

		assertThatCode(() -> topicService.rejectDuplicateWord(topic(), item("banana"))).doesNotThrowAnyException();
	}

	@Test
	void templateWithoutTargetWordCannotTellSoItSaves() {
		when(templateFieldRepository.findByTemplateId(3L)).thenReturn(List.of());

		assertThatCode(() -> topicService.rejectDuplicateWord(topic(), item("apple"))).doesNotThrowAnyException();
		verify(topicItemAttributeValueRepository, never()).findItemValues(any(), any());
	}

	@Test
	void topicWithoutTemplateSaves() {
		assertThatCode(() -> topicService.rejectDuplicateWord(Topic.builder().id(9L).build(), item("apple")))
				.doesNotThrowAnyException();
	}
}
