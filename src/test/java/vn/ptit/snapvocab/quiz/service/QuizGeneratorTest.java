package vn.ptit.snapvocab.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.ptit.snapvocab.quiz.domain.Quiz;
import vn.ptit.snapvocab.quiz.domain.QuizMatchingTile;
import vn.ptit.snapvocab.quiz.domain.QuizOption;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver.ItemContent;
import vn.ptit.snapvocab.quiz.exception.QuizException;

class QuizGeneratorTest {

	private final QuizGenerator generator = new QuizGenerator(new Random(42));

	private static ItemContent item(long id, String word, String translation, String... examples) {
		return new ItemContent(id, word, translation, List.of(examples));
	}

	private static List<ItemContent> vocabulary(int size) {
		List<ItemContent> items = new ArrayList<>();
		IntStream.rangeClosed(1, size).forEach(i -> items.add(item(i, "word" + i, "nghĩa " + i,
				"I use word" + i + " every day.")));
		return items;
	}

	private static QuizException failure(Runnable action) {
		try {
			action.run();
		} catch (QuizException e) {
			return e;
		}
		return null;
	}

	private static QuizErrorCode codeOf(Runnable action) {
		QuizException e = failure(action);
		return e == null ? null : e.getCode();
	}

	@Test
	void allowedCountsAreExactlyTheMobileChoices() {
		assertThat(QuizGenerator.ALLOWED_COUNTS).containsExactlyInAnyOrder(5, 10, 20);
	}

	@Test
	void mcqEnViHasFourDistinctOptionsAndExactlyOneCorrectKey() {
		Quiz quiz = new Quiz();
		generator.populateMcq(quiz, vocabulary(12), QuizDirection.EN_VI, 10);

		assertThat(quiz.getQuestions()).hasSize(10);
		for (QuizQuestion q : quiz.getQuestions()) {
			assertThat(q.getPrompt()).startsWith("word");
			assertThat(q.getCorrectAnswer()).startsWith("nghĩa");
			assertThat(q.getOptions()).hasSize(4);
			assertThat(q.getOptions()).extracting(QuizOption::getLabel).doesNotHaveDuplicates();
			assertThat(q.getOptions()).extracting(QuizOption::getOptionKey).doesNotHaveDuplicates()
					.contains(q.getCorrectOptionKey());
			QuizOption correct = q.getOptions().stream()
					.filter(o -> o.getOptionKey().equals(q.getCorrectOptionKey())).findFirst().orElseThrow();
			assertThat(correct.getLabel()).isEqualTo(q.getCorrectAnswer());
		}
	}

	@Test
	void mcqViEnSwapsPromptAndAnswer() {
		Quiz quiz = new Quiz();
		generator.populateMcq(quiz, vocabulary(8), QuizDirection.VI_EN, 5);

		assertThat(quiz.getQuestions()).hasSize(5).allSatisfy(q -> {
			assertThat(q.getPrompt()).startsWith("nghĩa");
			assertThat(q.getCorrectAnswer()).startsWith("word");
		});
	}

	@Test
	void mcqNeverUsesADistractorThatAlsoAnswersThePrompt() {
		List<ItemContent> items = new ArrayList<>(vocabulary(6));
		items.add(item(100, "home", "nhà"));
		items.add(item(101, "house", "nhà"));
		for (int seed = 0; seed < 20; seed++) {
			Quiz quiz = new Quiz();
			new QuizGenerator(new Random(seed)).populateMcq(quiz, items, QuizDirection.VI_EN, 5);
			for (QuizQuestion q : quiz.getQuestions()) {
				if (q.getPrompt().equals("nhà")) {
					assertThat(q.getOptions()).extracting(QuizOption::getLabel)
							.containsOnlyOnce(q.getCorrectAnswer())
							.doesNotContain(q.getCorrectAnswer().equals("home") ? "house" : "home");
				}
			}
		}
	}

	@Test
	void mcqNeedsFourDistinctAnswers() {
		List<ItemContent> items = List.of(item(1, "a", "x"), item(2, "b", "x"), item(3, "c", "y"),
				item(4, "d", "z"), item(5, "e", "Z "));
		assertThat(codeOf(() -> generator.populateMcq(new Quiz(), items, QuizDirection.EN_VI, 5)))
				.isEqualTo(QuizErrorCode.NOT_ENOUGH_DISTINCT_ANSWERS);
	}

	@Test
	void missingNativeTranslationIsIneligibleWithNoFallback() {
		List<ItemContent> items = new ArrayList<>(vocabulary(4));
		items.add(item(9, "orphan", null));
		assertThat(codeOf(() -> generator.populateMcq(new Quiz(), items, QuizDirection.EN_VI, 5)))
				.isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
	}

	@Test
	void missingTargetWordIsIneligible() {
		List<ItemContent> items = new ArrayList<>(vocabulary(4));
		items.add(item(9, null, "nghĩa"));
		assertThat(codeOf(() -> generator.populateMatching(new Quiz(), items, QuizDirection.EN_VI, 5)))
				.isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
	}

	@Test
	void insufficientPoolFailsWithBothCountsAndNeverBuildsASmallerQuiz() {
		Quiz quiz = new Quiz();
		QuizException e = failure(() -> generator.populateMcq(quiz, vocabulary(9), QuizDirection.EN_VI, 10));

		assertThat(e).isNotNull();
		assertThat(e.getCode()).isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
		assertThat(e.getDetails()).asString().contains("eligibleCount=9").contains("requestedCount=10");
		assertThat(quiz.getQuestions()).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({ "5, 1", "10, 2", "20, 4" })
	void matchingUsesFullRoundsOfFive(int requestedCount, int expectedRounds) {
		Quiz quiz = new Quiz();
		generator.populateMatching(quiz, vocabulary(25), QuizDirection.EN_VI, requestedCount);

		assertThat(quiz.getQuestions()).hasSize(requestedCount);
		assertThat(quiz.getTiles()).hasSize(requestedCount);
		assertThat(quiz.getQuestions().stream().map(QuizQuestion::getRoundNo).distinct())
				.containsExactlyElementsOf(IntStream.rangeClosed(1, expectedRounds).boxed().toList());
		for (int round = 1; round <= expectedRounds; round++) {
			int r = round;
			assertThat(quiz.getQuestions()).filteredOn(q -> q.getRoundNo() == r).hasSize(5);
			assertThat(quiz.getTiles().stream().filter(t -> t.getRoundNo() == r).map(QuizMatchingTile::getDisplayOrder))
					.containsExactlyInAnyOrder(1, 2, 3, 4, 5);
		}
	}

	@Test
	void matchingPairsCarryOpaqueKeysAndMatchingTiles() {
		Quiz quiz = new Quiz();
		generator.populateMatching(quiz, vocabulary(12), QuizDirection.EN_VI, 10);

		for (QuizQuestion q : quiz.getQuestions()) {
			assertThat(q.getLeftKey()).matches("l-[0-9a-f]{16}");
			assertThat(q.getCorrectRightKey()).matches("r-[0-9a-f]{16}");
			assertThat(quiz.getTiles()).anySatisfy(t -> {
				assertThat(t.getTileKey()).isEqualTo(q.getCorrectRightKey());
				assertThat(t.getRoundNo()).isEqualTo(q.getRoundNo());
				assertThat(t.getLabel()).isEqualTo(q.getCorrectAnswer());
			});
		}
	}

	@Test
	void matchingExcludesPairsThatWouldBeAmbiguous() {
		List<ItemContent> items = List.of(item(1, "home", "nhà"), item(2, "house", "nhà"), item(3, "cat", "mèo"),
				item(4, "dog", "chó"), item(5, "sun", "mặt trời"), item(6, "moon", "mặt trăng"));
		Quiz quiz = new Quiz();
		generator.populateMatching(quiz, items, QuizDirection.EN_VI, 5);
		assertThat(quiz.getQuestions()).extracting(QuizQuestion::getCorrectAnswer).doesNotHaveDuplicates();

		List<ItemContent> tooFew = new ArrayList<>(items.subList(0, 5));
		Collections.shuffle(tooFew, new Random(1));
		assertThat(codeOf(() -> generator.populateMatching(new Quiz(), tooFew, QuizDirection.EN_VI, 5)))
				.isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
	}

	@Test
	void fillBlankHidesEveryWholeWordOccurrenceCaseInsensitively() {
		List<ItemContent> items = List.of(
				item(1, "run", null, "Run fast, run far; the runner runs."),
				item(2, "cat", null, "The cat sat."),
				item(3, "dog", null, "A dog barks."),
				item(4, "sun", null, "The sun rises."),
				item(5, "star", null, "A star shines."));
		Quiz quiz = new Quiz();
		generator.populateFillBlank(quiz, items, 5);

		QuizQuestion run = quiz.getQuestions().stream().filter(q -> q.getCorrectAnswer().equals("run"))
				.findFirst().orElseThrow();
		assertThat(run.getPrompt()).isEqualTo("_____ fast, _____ far; the runner runs.");
		assertThat(quiz.getQuestions()).hasSize(5).allSatisfy(q -> assertThat(q.getOptions()).isEmpty());
	}

	@Test
	void fillBlankDoesNoStemming() {
		List<ItemContent> items = new ArrayList<>(vocabulary(4));
		items.add(item(9, "abandon", null, "They abandoned the car."));
		assertThat(codeOf(() -> generator.populateFillBlank(new Quiz(), items, 5)))
				.isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
	}

	@Test
	void fillBlankUsesOnlyExamplesContainingTheTargetAndQuotesRegexCharacters() {
		List<ItemContent> items = new ArrayList<>(vocabulary(4));
		items.add(item(9, "C++", null, "No match here.", "I write C++ daily."));
		Quiz quiz = new Quiz();
		generator.populateFillBlank(quiz, items, 5);
		assertThat(quiz.getQuestions()).anySatisfy(q -> {
			assertThat(q.getCorrectAnswer()).isEqualTo("C++");
			assertThat(q.getPrompt()).isEqualTo("I write _____ daily.");
		});
	}

	@Test
	void fillBlankMatchesMultiWordPhrases() {
		assertThat(QuizGenerator.occurrence("look after").matcher("Please LOOK AFTER it.").find()).isTrue();
		assertThat(QuizGenerator.occurrence("look after").matcher("Please look afterwards.").find()).isFalse();
	}

	@Test
	void missingExampleSentenceIsIneligibleForFillBlank() {
		List<ItemContent> items = new ArrayList<>(vocabulary(4));
		items.add(item(9, "lonely", "cô đơn"));
		assertThat(codeOf(() -> generator.populateFillBlank(new Quiz(), items, 5)))
				.isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
	}

	@Test
	void normalizeTrimsCollapsesWhitespaceAndIgnoresCase() {
		assertThat(QuizGenerator.normalize("  Look   AFTER ")).isEqualTo("look after");
		assertThat(QuizGenerator.normalize(null)).isEmpty();
	}
}
