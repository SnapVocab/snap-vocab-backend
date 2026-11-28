package vn.ptit.snapvocab.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SoundAlikeTest {

	private static final List<String> POOL = List.of("chip", "shop", "sheep", "shape", "chop", "ship", "sip",
			"banana", "computer", "see", "sea", "tea", "say", "she", "right", "write", "rat", "ride", "light",
			"TV", "DJ", "CD", "DVD", "CV", "Sunday", "Friday", "Paris", "pain", "pine", "pan", "look after",
			"look back", "look for", "get on", "elephant", "night", "knight", "knit", "nut", "their", "there",
			"tear", "three", "tree", "free", "throw");

	private static List<String> pick(String word) {
		return SoundAlike.distractors(word, POOL, Set.of(), 3, new Random(1));
	}

	@Test
	void picksWordsThatSoundLikeTheHeardOne() {
		List<String> options = pick("ship");
		assertThat(options).hasSize(3).doesNotContain("ship")
				.allMatch(o -> Set.of("chip", "shop", "sheep", "shape", "chop", "sip", "she").contains(o));
	}

	@Test
	void neverOffersAHomophoneTheEarCannotTellApart() {
		for (int seed = 0; seed < 20; seed++) {
			Random random = new Random(seed);
			assertThat(SoundAlike.distractors("see", POOL, Set.of(), 3, random)).doesNotContain("sea");
			assertThat(SoundAlike.distractors("right", POOL, Set.of(), 3, random)).doesNotContain("write");
			assertThat(SoundAlike.distractors("night", POOL, Set.of(), 3, random)).doesNotContain("knight");
			assertThat(SoundAlike.distractors("their", POOL, Set.of(), 3, random)).doesNotContain("there");
		}
	}

	@Test
	void recognisesHomophonesFromSpellingRules() {
		assertThat(SoundAlike.Sound.spoken("meet")).isEqualTo(SoundAlike.Sound.spoken("meat"));
		assertThat(SoundAlike.Sound.spoken("plane")).isEqualTo(SoundAlike.Sound.spoken("plain"));
		assertThat(SoundAlike.Sound.spoken("ship")).isNotEqualTo(SoundAlike.Sound.spoken("sheep"));
	}

	@Test
	void keepsPhrasesWithPhrasesAndAcronymsWithAcronyms() {
		assertThat(pick("look at")).allMatch(o -> o.contains(" "));
		assertThat(pick("TV")).allMatch(o -> o.equals(o.toUpperCase()));
	}

	@Test
	void writesOptionsInTheSameCaseAsTheWord() {
		assertThat(pick("Monday")).allMatch(o -> Character.isUpperCase(o.charAt(0)));
		// A lowercase word never gets a proper noun ("paris" would look made up).
		assertThat(pick("pain")).doesNotContain("paris", "Paris");
	}

	@Test
	void prefersWordsFromTheQuizTopicWhenTheySoundAsClose() {
		List<String> pool = List.of("chip", "chop", "shop", "sheep", "shape", "shin", "sheet");
		long fromTopic = 0;
		for (int seed = 0; seed < 50; seed++) {
			fromTopic += SoundAlike.distractors("ship", pool, Set.of("sheet", "shin"), 3, new Random(seed)).stream()
					.filter(Set.of("sheet", "shin")::contains).count();
		}
		assertThat(fromTopic).isGreaterThan(0);
	}

	@Test
	void returnsFewerWhenThePoolHasNothingSuitable() {
		assertThat(SoundAlike.distractors("ship", List.of("ship", "SHIP", "Ship"), Set.of(), 3, new Random(1)))
				.isEmpty();
	}

	@Test
	void levenshteinCountsEdits() {
		assertThat(SoundAlike.levenshtein("ship", "sheep")).isEqualTo(2);
		assertThat(SoundAlike.levenshtein("", "abc")).isEqualTo(3);
	}
}
