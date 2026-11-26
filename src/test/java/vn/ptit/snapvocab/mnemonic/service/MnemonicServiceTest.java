package vn.ptit.snapvocab.mnemonic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import vn.ptit.snapvocab.mnemonic.domain.MnemonicType;
import vn.ptit.snapvocab.mnemonic.domain.WordMnemonic;
import vn.ptit.snapvocab.mnemonic.dto.LeechWordDTO;
import vn.ptit.snapvocab.mnemonic.exception.MnemonicErrorCode;
import vn.ptit.snapvocab.mnemonic.exception.MnemonicException;
import vn.ptit.snapvocab.mnemonic.repository.WordMnemonicRepository;
import vn.ptit.snapvocab.story.service.LlmGatewayService;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.FsrsRecord;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;
import vn.ptit.snapvocab.vocab.domain.TopicItem;

@ExtendWith(MockitoExtension.class)
class MnemonicServiceTest {

	private static final Long USER_ID = 7L;
	private static final Long ITEM_ID = 21L;
	private static final String ANSWER = """
			```json
			{"type":"sound_alike","keyword":"hét đi Tết","mnemonic":"\\"hesitate\\" (do dự) nghe như \\"hét đi Tết\\"."}
			```""";

	@Mock private StudyQueueRepository studyQueueRepository;
	@Mock private WordMnemonicRepository mnemonicRepository;
	@Mock private UserRepository userRepository;
	@Mock private WordFactsReader wordFactsReader;
	@Mock private LlmGatewayService llmGateway;
	@Mock private RedissonClient redissonClient;
	@Mock private RAtomicLong quota;

	private MnemonicService service;
	private TopicItem item;

	@BeforeEach
	void setUp() {
		service = new MnemonicService(studyQueueRepository, mnemonicRepository, userRepository, wordFactsReader,
				llmGateway, redissonClient, new ObjectMapper());
		item = new TopicItem();
		item.setId(ITEM_ID);
		lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(quota);
		lenient().when(wordFactsReader.read(List.of(ITEM_ID)))
				.thenReturn(Map.of(ITEM_ID, new WordFacts("hesitate", null, "do dự", null, null)));
		lenient().when(mnemonicRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
	}

	private void forgotten(int times) {
		FsrsRecord record = FsrsRecord.builder().topicItem(item).forgetCount(times).build();
		when(studyQueueRepository.findByUser_IdAndTopicItem_Id(USER_ID, ITEM_ID)).thenReturn(Optional.of(record));
	}

	private void aiAnswers(String raw) {
		when(llmGateway.generateAndParse(anyString(), anyString(), any())).thenAnswer(inv -> {
			Function<String, Object> parser = inv.getArgument(2);
			return new LlmGatewayService.ValidatedGeneration<>(parser.apply(raw), "Gemini", "gemini-2.5-flash", 5L);
		});
	}

	@Test
	void aLeechGetsAParsedMnemonicAndUsesOneGeneration() {
		forgotten(2);
		when(quota.incrementAndGet()).thenReturn(3L);
		aiAnswers(ANSWER);

		var result = service.generate(USER_ID, ITEM_ID);

		assertThat(result.mnemonic().type()).isEqualTo(MnemonicType.SOUND_ALIKE);
		assertThat(result.mnemonic().keyword()).isEqualTo("hét đi Tết");
		assertThat(result.mnemonic().content()).contains("hesitate").contains("do dự");
		assertThat(result.remainingToday()).isEqualTo(7);
		ArgumentCaptor<WordMnemonic> saved = ArgumentCaptor.forClass(WordMnemonic.class);
		verify(mnemonicRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getProviderName()).isEqualTo("Gemini");
		verify(llmGateway).generateAndParse(eq(MnemonicService.SYSTEM_PROMPT), contains("Từ tiếng Anh: hesitate"), any());
		verify(quota, never()).decrementAndGet();
	}

	@Test
	void regeneratingReplacesTheLearnersExistingMnemonic() {
		forgotten(3);
		when(quota.incrementAndGet()).thenReturn(1L);
		WordMnemonic existing = new WordMnemonic();
		existing.setTopicItem(item);
		existing.setContent("cũ");
		when(mnemonicRepository.findByUserIdAndTopicItemId(USER_ID, ITEM_ID)).thenReturn(Optional.of(existing));
		aiAnswers(ANSWER);

		service.generate(USER_ID, ITEM_ID);

		assertThat(existing.getContent()).contains("hesitate");
		verify(mnemonicRepository).saveAndFlush(existing);
	}

	@Test
	void aWordForgottenOnceIsNotALeech() {
		forgotten(1);

		assertThatThrownBy(() -> service.generate(USER_ID, ITEM_ID))
				.isInstanceOfSatisfying(MnemonicException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(MnemonicErrorCode.NOT_A_LEECH));
		verify(llmGateway, never()).generateAndParse(anyString(), anyString(), any());
	}

	@Test
	void aWordNeverReviewedIsNotFound() {
		when(studyQueueRepository.findByUser_IdAndTopicItem_Id(USER_ID, ITEM_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.generate(USER_ID, ITEM_ID))
				.isInstanceOfSatisfying(MnemonicException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(MnemonicErrorCode.ITEM_NOT_FOUND));
	}

	@Test
	void theEleventhGenerationOfTheDayIsRefusedWithoutCallingTheAi() {
		forgotten(2);
		when(quota.incrementAndGet()).thenReturn(11L);

		assertThatThrownBy(() -> service.generate(USER_ID, ITEM_ID))
				.isInstanceOfSatisfying(MnemonicException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(MnemonicErrorCode.MNEMONIC_QUOTA_EXCEEDED));
		verify(quota).decrementAndGet();
		verify(llmGateway, never()).generateAndParse(anyString(), anyString(), any());
	}

	@Test
	void aFailedAiCallGivesTheGenerationBack() {
		forgotten(2);
		when(quota.incrementAndGet()).thenReturn(1L);
		when(llmGateway.generateAndParse(anyString(), anyString(), any()))
				.thenThrow(new IllegalStateException("All configured AI providers failed"));

		assertThatThrownBy(() -> service.generate(USER_ID, ITEM_ID))
				.isInstanceOfSatisfying(MnemonicException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(MnemonicErrorCode.AI_UNAVAILABLE));
		verify(quota).decrementAndGet();
		verify(mnemonicRepository, never()).saveAndFlush(any());
	}

	@Test
	void oneUnusableAnswerIsRetriedBeforeTheLearnerSeesAnError() {
		forgotten(2);
		when(quota.incrementAndGet()).thenReturn(1L);
		when(llmGateway.generateAndParse(anyString(), anyString(), any()))
				.thenThrow(new IllegalStateException("All configured AI providers failed: Mnemonic does not mention the word"))
				.thenAnswer(inv -> {
					Function<String, Object> parser = inv.getArgument(2);
					return new LlmGatewayService.ValidatedGeneration<>(parser.apply(ANSWER), "DeepSeek", "deepseek-chat", 5L);
				});

		var result = service.generate(USER_ID, ITEM_ID);

		assertThat(result.mnemonic().content()).contains("hesitate");
		verify(llmGateway, times(2)).generateAndParse(anyString(), anyString(), any());
		verify(quota, never()).decrementAndGet();
	}

	@Test
	void theLeechListIsCappedButReportsHowManyThereAre() {
		FsrsRecord record = FsrsRecord.builder().topicItem(item).forgetCount(3).build();
		when(studyQueueRepository.findLeeches(eq(USER_ID), eq(MnemonicService.MIN_FORGET_COUNT), eq(CardState.SUSPENDED),
				any(Pageable.class))).thenReturn(List.of(record));
		when(studyQueueRepository.countLeeches(USER_ID, MnemonicService.MIN_FORGET_COUNT, CardState.SUSPENDED))
				.thenReturn(41L);
		when(quota.get()).thenReturn(2L);

		var list = service.leeches(USER_ID, 5000);

		assertThat(list.total()).isEqualTo(41);
		assertThat(list.items()).extracting(LeechWordDTO::word).containsExactly("hesitate");
		ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
		verify(studyQueueRepository).findLeeches(eq(USER_ID), eq(MnemonicService.MIN_FORGET_COUNT),
				eq(CardState.SUSPENDED), page.capture());
		assertThat(page.getValue().getPageSize()).isEqualTo(MnemonicService.MAX_LEECH_LIST_SIZE);
	}

	@Test
	void anUnknownTypeFallsBackToStoryAndAnOverlongAnswerIsRejected() {
		var parsed = service.parse("{\"type\":\"RHYME\",\"mnemonic\":\"Một câu chuyện ngắn.\"}");
		assertThat(parsed.type()).isEqualTo(MnemonicType.STORY);
		assertThat(parsed.keyword()).isNull();

		String tooLong = "a".repeat(MnemonicService.MAX_CONTENT_LENGTH + 1);
		assertThatThrownBy(() -> service.parse("{\"type\":\"STORY\",\"mnemonic\":\"" + tooLong + "\"}"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.parse("không phải JSON")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.parse("{\"type\":\"STORY\",\"mnemonic\":\"  \"}"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void thePromptCarriesWhatTheCardSaysAndHowOftenItWasForgotten() {
		String prompt = MnemonicService.userPrompt(
				new WordFacts("ubiquitous", "/juːˈbɪkwɪtəs/", "có mặt khắp nơi", null, "Phones are ubiquitous."), 4, null);

		assertThat(prompt).contains("Từ tiếng Anh: ubiquitous", "Phiên âm: /juːˈbɪkwɪtəs/",
				"Nghĩa tiếng Việt: có mặt khắp nơi", "Ví dụ: Phones are ubiquitous.", "đã quên từ này 4 lần")
				.doesNotContain("Định nghĩa", "Mẹo cũ");
	}

	@Test
	void anAnswerThatNeverNamesTheWordIsRejectedSoTheNextProviderIsTried() {
		String answer = "{\"type\":\"STORY\",\"mnemonic\":\"Đứng mãi trước cửa mà chưa dám bước vào.\"}";

		assertThatThrownBy(() -> service.parse(answer, "hesitate")).isInstanceOf(IllegalArgumentException.class);
		assertThat(service.parse("{\"type\":\"WORDPLAY\",\"mnemonic\":\"Give Up: đưa lên rồi buông, từ bỏ.\"}",
				"give up").type()).isEqualTo(MnemonicType.WORDPLAY);
	}

	@Test
	void regeneratingAsksForADifferentIdeaThanTheOldMnemonic() {
		WordMnemonic previous = new WordMnemonic();
		previous.setType(MnemonicType.SOUND_ALIKE);
		previous.setKeyword("cúp");
		previous.setContent("cup đọc như \"cúp\"");

		String prompt = MnemonicService.userPrompt(new WordFacts("cup", null, "cái cốc", null, null), 2, previous);

		assertThat(prompt).contains("Mẹo cũ người học chưa ưng", "cup đọc như", "móc nhớ \"cúp\"", "KHÁC HẲN",
				"kiểu khác SOUND_ALIKE");
	}
}
