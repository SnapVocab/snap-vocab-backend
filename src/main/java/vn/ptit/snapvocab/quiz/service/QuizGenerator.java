package vn.ptit.snapvocab.quiz.service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.quiz.domain.Quiz;
import vn.ptit.snapvocab.quiz.domain.QuizMatchingTile;
import vn.ptit.snapvocab.quiz.domain.QuizOption;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver.ItemContent;
import vn.ptit.snapvocab.quiz.exception.QuizException;

@Component
public class QuizGenerator {

	public static final Set<Integer> ALLOWED_COUNTS = Set.of(5, 10, 20);
	static final int MCQ_OPTIONS = 4;
	static final int PAIRS_PER_ROUND = 5;
	static final String BLANK = "_____";

	private final Random random;

	public QuizGenerator() {
		this(new SecureRandom());
	}

	QuizGenerator(Random random) {
		this.random = random;
	}

	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return Normalizer.normalize(text, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
	}

	public void populateMcq(Quiz quiz, List<ItemContent> items, QuizDirection direction, int count) {
		List<ItemContent> eligible = items.stream()
				.filter(i -> i.targetWord() != null && i.nativeTranslation() != null)
				.toList();
		requirePool(eligible.size(), count);
		Function<ItemContent, String> prompt = promptSide(direction);
		Function<ItemContent, String> answer = answerSide(direction);
		if (eligible.stream().map(i -> normalize(answer.apply(i))).distinct().count() < MCQ_OPTIONS) {
			throw new QuizException(QuizErrorCode.NOT_ENOUGH_DISTINCT_ANSWERS,
					"MCQ needs at least " + MCQ_OPTIONS + " distinct answers",
					Map.of("requestedCount", count, "eligibleCount", eligible.size()));
		}

		List<ItemContent> shuffled = shuffled(eligible);
		Set<String> usedPrompts = new HashSet<>();
		int position = 1;
		for (ItemContent item : shuffled) {
			if (position > count) {
				break;
			}
			String promptKey = normalize(prompt.apply(item));
			if (!usedPrompts.add(promptKey)) {
				continue;
			}
			List<String> distractors = distractors(item, shuffled, prompt, answer);
			if (distractors.size() < MCQ_OPTIONS - 1) {
				continue;
			}
			QuizQuestion question = QuizQuestion.builder()
					.position(position++)
					.topicItemId(item.topicItemId())
					.prompt(prompt.apply(item))
					.correctAnswer(answer.apply(item))
					.build();
			List<String> labels = new ArrayList<>(distractors);
			labels.add(answer.apply(item));
			Collections.shuffle(labels, random);
			Set<String> keys = new HashSet<>();
			for (int order = 0; order < labels.size(); order++) {
				String key = newKey("o", keys);
				if (labels.get(order).equals(answer.apply(item)) && question.getCorrectOptionKey() == null) {
					question.setCorrectOptionKey(key);
				}
				question.addOption(QuizOption.builder().optionKey(key).label(labels.get(order))
						.displayOrder(order + 1).build());
			}
			quiz.addQuestion(question);
		}
		requirePool(position - 1, count);
	}

	private static List<String> distractors(ItemContent item, List<ItemContent> pool,
			Function<ItemContent, String> prompt, Function<ItemContent, String> answer) {
		Set<String> seen = new HashSet<>();
		seen.add(normalize(answer.apply(item)));
		String itemPrompt = normalize(prompt.apply(item));
		List<String> result = new ArrayList<>();
		for (ItemContent other : pool) {
			if (result.size() == MCQ_OPTIONS - 1) {
				break;
			}
			if (other == item || normalize(prompt.apply(other)).equals(itemPrompt)) {
				continue;
			}
			if (seen.add(normalize(answer.apply(other)))) {
				result.add(answer.apply(other));
			}
		}
		return result;
	}

	public void populateMatching(Quiz quiz, List<ItemContent> items, QuizDirection direction, int count) {
		Function<ItemContent, String> left = promptSide(direction);
		Function<ItemContent, String> right = answerSide(direction);
		Set<String> lefts = new HashSet<>();
		Set<String> rights = new HashSet<>();
		List<ItemContent> selected = new ArrayList<>();
		for (ItemContent item : shuffled(items)) {
			if (item.targetWord() == null || item.nativeTranslation() == null) {
				continue;
			}
			String l = normalize(left.apply(item));
			String r = normalize(right.apply(item));
			if (!lefts.contains(l) && !rights.contains(r)) {
				lefts.add(l);
				rights.add(r);
				selected.add(item);
			}
		}
		requirePool(selected.size(), count);

		Set<String> keys = new HashSet<>();
		for (int start = 0, position = 1; start < count; start += PAIRS_PER_ROUND) {
			int roundNo = start / PAIRS_PER_ROUND + 1;
			List<ItemContent> round = selected.subList(start, Math.min(start + PAIRS_PER_ROUND, count));
			List<Integer> leftOrder = shuffledOrder(round.size());
			List<Integer> rightOrder = shuffledOrder(round.size());
			for (int i = 0; i < round.size(); i++) {
				ItemContent item = round.get(i);
				String rightKey = newKey("r", keys);
				quiz.addQuestion(QuizQuestion.builder()
						.position(position++)
						.roundNo(roundNo)
						.topicItemId(item.topicItemId())
						.prompt(left.apply(item))
						.correctAnswer(right.apply(item))
						.leftKey(newKey("l", keys))
						.leftDisplayOrder(leftOrder.get(i))
						.correctRightKey(rightKey)
						.build());
				quiz.addTile(QuizMatchingTile.builder()
						.roundNo(roundNo)
						.tileKey(rightKey)
						.label(right.apply(item))
						.displayOrder(rightOrder.get(i))
						.build());
			}
		}
	}

	public void populateFillBlank(Quiz quiz, List<ItemContent> items, int count) {
		List<ItemContent> eligible = new ArrayList<>();
		for (ItemContent item : items) {
			if (item.targetWord() != null && item.exampleSentences().stream()
					.anyMatch(s -> occurrence(item.targetWord()).matcher(s).find())) {
				eligible.add(item);
			}
		}
		requirePool(eligible.size(), count);

		Set<String> usedTargets = new HashSet<>();
		int position = 1;
		for (ItemContent item : shuffled(eligible)) {
			if (position > count) {
				break;
			}
			if (!usedTargets.add(normalize(item.targetWord()))) {
				continue;
			}
			Pattern pattern = occurrence(item.targetWord());
			List<String> candidates = item.exampleSentences().stream()
					.filter(s -> pattern.matcher(s).find())
					.toList();
			String sentence = candidates.get(random.nextInt(candidates.size()));
			quiz.addQuestion(QuizQuestion.builder()
					.position(position++)
					.topicItemId(item.topicItemId())
					.prompt(pattern.matcher(sentence).replaceAll(Matcher.quoteReplacement(BLANK)))
					.correctAnswer(item.targetWord())
					.build());
		}
		requirePool(position - 1, count);
	}

	static Pattern occurrence(String targetWord) {
		return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(targetWord.trim()) + "(?![\\p{L}\\p{N}_])",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	private static Function<ItemContent, String> promptSide(QuizDirection direction) {
		return direction == QuizDirection.VI_EN ? ItemContent::nativeTranslation : ItemContent::targetWord;
	}

	private static Function<ItemContent, String> answerSide(QuizDirection direction) {
		return direction == QuizDirection.VI_EN ? ItemContent::targetWord : ItemContent::nativeTranslation;
	}

	private static void requirePool(int eligible, int requested) {
		if (eligible < requested) {
			throw new QuizException(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS,
					"Only " + eligible + " eligible items for " + requested + " requested",
					Map.of("requestedCount", requested, "eligibleCount", eligible));
		}
	}

	private <T> List<T> shuffled(List<T> source) {
		List<T> copy = new ArrayList<>(source);
		Collections.shuffle(copy, random);
		return copy;
	}

	private List<Integer> shuffledOrder(int size) {
		List<Integer> order = new ArrayList<>();
		for (int i = 1; i <= size; i++) {
			order.add(i);
		}
		Collections.shuffle(order, random);
		return order;
	}

	private String newKey(String prefix, Set<String> used) {
		byte[] bytes = new byte[8];
		String key;
		do {
			random.nextBytes(bytes);
			key = prefix + "-" + HexFormat.of().formatHex(bytes);
		} while (!used.add(key));
		return key;
	}
}
