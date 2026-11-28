package vn.ptit.snapvocab.quiz.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.codec.language.DoubleMetaphone;

/**
 * Wrong answers for a listening question: real words that sound close to the heard one ("ship" → sheep, chip,
 * shop), so the learner has to listen rather than guess. A word that sounds exactly the same ("sea"/"see") is never
 * offered: nobody could tell them apart by ear.
 */
final class SoundAlike {

	/** Candidates are drawn at random among this many best matches, so a word does not always get the same three. */
	static final int SHORTLIST = 6;
	private static final int MAX_LENGTH_GAP = 3;
	private static final double TOPIC_BONUS = 0.08;
	private static final double CASE_BONUS = 0.15;
	private static final Pattern WORD = Pattern.compile("[a-z][a-z' -]*");

	/** Common homophones the spelling rules below cannot see. */
	private static final List<Set<String>> HOMOPHONES = List.of(
			Set.of("their", "there", "they're"), Set.of("to", "two", "too"), Set.of("for", "four", "fore"),
			Set.of("buy", "by", "bye"), Set.of("know", "no"), Set.of("knew", "new"), Set.of("hear", "here"),
			Set.of("see", "sea"), Set.of("right", "write", "rite"), Set.of("son", "sun"), Set.of("flour", "flower"),
			Set.of("one", "won"), Set.of("ate", "eight"), Set.of("wait", "weight"), Set.of("meet", "meat"),
			Set.of("week", "weak"), Set.of("blue", "blew"), Set.of("hour", "our"), Set.of("mail", "male"),
			Set.of("piece", "peace"), Set.of("plain", "plane"), Set.of("road", "rode", "rowed"),
			Set.of("sail", "sale"), Set.of("tail", "tale"), Set.of("pair", "pear", "pare"), Set.of("bear", "bare"),
			Set.of("deer", "dear"), Set.of("hole", "whole"), Set.of("knight", "night"), Set.of("knot", "not"),
			Set.of("made", "maid"), Set.of("red", "read"), Set.of("sent", "cent", "scent"),
			Set.of("steal", "steel"), Set.of("wear", "where", "ware"), Set.of("wood", "would"),
			Set.of("weather", "whether"), Set.of("allowed", "aloud"), Set.of("brake", "break"),
			Set.of("cell", "sell"), Set.of("fair", "fare"), Set.of("hair", "hare"), Set.of("heal", "heel"),
			Set.of("him", "hymn"), Set.of("i", "eye", "aye"), Set.of("in", "inn"), Set.of("its", "it's"),
			Set.of("pain", "pane"), Set.of("peer", "pier"), Set.of("principal", "principle"),
			Set.of("rain", "reign", "rein"), Set.of("rose", "rows"), Set.of("stair", "stare"),
			Set.of("threw", "through"), Set.of("toe", "tow"), Set.of("waist", "waste"), Set.of("way", "weigh"),
			Set.of("which", "witch"), Set.of("berry", "bury"), Set.of("cereal", "serial"),
			Set.of("coarse", "course"), Set.of("dew", "due"), Set.of("flu", "flew"), Set.of("grate", "great"),
			Set.of("idle", "idol"), Set.of("lead", "led"), Set.of("missed", "mist"),
			Set.of("morning", "mourning"), Set.of("past", "passed"), Set.of("scene", "seen"),
			Set.of("sight", "site", "cite"), Set.of("some", "sum"), Set.of("suite", "sweet"), Set.of("tea", "tee"),
			Set.of("vain", "vein"), Set.of("wine", "whine"), Set.of("be", "bee"), Set.of("bean", "been"),
			Set.of("board", "bored"), Set.of("ceiling", "sealing"), Set.of("check", "cheque"),
			Set.of("die", "dye"), Set.of("feet", "feat"), Set.of("find", "fined"), Set.of("guessed", "guest"),
			Set.of("hi", "high"), Set.of("higher", "hire"), Set.of("key", "quay"), Set.of("loan", "lone"),
			Set.of("medal", "meddle"), Set.of("naval", "navel"), Set.of("oh", "owe"), Set.of("pause", "paws"),
			Set.of("poor", "pour", "pore"), Set.of("profit", "prophet"), Set.of("root", "route"),
			Set.of("sew", "so", "sow"), Set.of("sole", "soul"), Set.of("stationary", "stationery"),
			Set.of("tide", "tied"), Set.of("warn", "worn"), Set.of("we", "wee"), Set.of("weak", "week"),
			Set.of("whose", "who's"), Set.of("your", "you're"), Set.of("ball", "bawl"), Set.of("band", "banned"),
			Set.of("bored", "board"), Set.of("bread", "bred"), Set.of("chews", "choose"), Set.of("days", "daze"),
			Set.of("fir", "fur"), Set.of("flea", "flee"), Set.of("groan", "grown"), Set.of("knows", "nose"),
			Set.of("mussel", "muscle"), Set.of("nun", "none"), Set.of("pedal", "peddle"), Set.of("plum", "plumb"),
			Set.of("raise", "rays"), Set.of("ring", "wring"), Set.of("sun", "son"), Set.of("tow", "toe"),
			Set.of("wail", "whale"), Set.of("won't", "wont"));

	private static final Map<String, Integer> HOMOPHONE_GROUP = homophoneIndex();

	private SoundAlike() {
	}

	/**
	 * Up to {@code count} words from {@code pool} that sound like {@code word}, written in the same case style.
	 * Words in {@code topicWords} (already lowercased) are slightly preferred: the learner has seen them.
	 */
	static List<String> distractors(String word, Collection<String> pool, Set<String> topicWords, int count,
			Random random) {
		String target = clean(word);
		if (target.isEmpty()) {
			return List.of();
		}
		Sound targetSound = Sound.of(target);
		int targetWords = target.split(" ").length;
		Shape targetShape = Shape.of(word);

		Map<String, Double> scores = new HashMap<>();
		for (String raw : pool) {
			String candidate = clean(raw);
			if (candidate.isEmpty() || candidate.equals(target) || scores.containsKey(candidate)
					|| !WORD.matcher(candidate).matches()
					|| candidate.split(" ").length != targetWords
					|| Math.abs(candidate.length() - target.length()) > MAX_LENGTH_GAP) {
				continue;
			}
			// Written in the target's style the options must still be real words: no "DEFY" next to "TV",
			// no "paris" next to "pain". A capitalised target ("Monday") prefers capitalised words ("Sunday").
			Shape shape = Shape.of(raw);
			if ((targetShape == Shape.ACRONYM) != (shape == Shape.ACRONYM)
					|| (targetShape == Shape.LOWER && shape != Shape.LOWER)) {
				continue;
			}
			Sound sound = Sound.of(candidate);
			if (sameSound(target, targetSound, candidate, sound)) {
				continue;
			}
			double score = similarity(target, targetSound, candidate, sound)
					+ (topicWords.contains(candidate) ? TOPIC_BONUS : 0)
					+ (targetShape == Shape.CAPITALIZED && shape == Shape.CAPITALIZED ? CASE_BONUS : 0);
			scores.put(candidate, score);
		}

		List<String> ranked = new ArrayList<>(scores.keySet());
		Collections.shuffle(ranked, random);
		ranked.sort(Comparator.comparingDouble((String c) -> scores.get(c)).reversed());

		List<String> shortlist = new ArrayList<>(ranked.subList(0, Math.min(Math.max(SHORTLIST, count), ranked.size())));
		Collections.shuffle(shortlist, random);
		List<String> picked = new ArrayList<>();
		for (String candidate : shortlist) {
			// Two wrong answers that sound alike would leave the learner only three real choices.
			if (picked.stream().noneMatch(p -> sameSound(p, Sound.of(p), candidate, Sound.of(candidate)))) {
				picked.add(candidate);
			}
			if (picked.size() == count) {
				break;
			}
		}
		// Fall back to the next best ones when the shortlist had near-twins.
		for (String candidate : ranked) {
			if (picked.size() == count) {
				break;
			}
			if (!picked.contains(candidate)) {
				picked.add(candidate);
			}
		}
		return picked.stream().map(p -> styleLike(word, p)).toList();
	}

	/** 0..1: how close the two sound (Double Metaphone) and look (spelling), plus a little for the same first sound. */
	static double similarity(String a, Sound aSound, String b, Sound bSound) {
		double sound = Math.max(closeness(aSound.primary(), bSound.primary()),
				closeness(aSound.alternate(), bSound.alternate()));
		double spelling = closeness(a, b);
		boolean sameStart = a.charAt(0) == b.charAt(0)
				|| (!aSound.primary().isEmpty() && !bSound.primary().isEmpty()
						&& aSound.primary().charAt(0) == bSound.primary().charAt(0));
		return 0.55 * sound + 0.35 * spelling + (sameStart ? 0.1 : 0);
	}

	/** Homophones: listed together, or the same once spelling is turned into rough sounds ("right" / "write"). */
	static boolean sameSound(String a, Sound aSound, String b, Sound bSound) {
		Integer group = HOMOPHONE_GROUP.get(a);
		if (group != null && group.equals(HOMOPHONE_GROUP.get(b))) {
			return true;
		}
		return aSound.spoken().equals(bSound.spoken());
	}

	static String clean(String text) {
		return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** "Monday" gets "Sunday", not "sunday": a lone capital would point at the answer. */
	static String styleLike(String word, String candidate) {
		String trimmed = word.trim();
		if (trimmed.length() > 1 && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))) {
			return candidate.toUpperCase(Locale.ROOT);
		}
		if (!trimmed.isEmpty() && Character.isUpperCase(trimmed.charAt(0))) {
			return Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
		}
		return candidate;
	}

	private static double closeness(String a, String b) {
		int longest = Math.max(a.length(), b.length());
		return longest == 0 ? 0 : 1 - (double) levenshtein(a, b) / longest;
	}

	static int levenshtein(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
				current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}

	private static Map<String, Integer> homophoneIndex() {
		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < HOMOPHONES.size(); i++) {
			for (String word : HOMOPHONES.get(i)) {
				index.putIfAbsent(word, i);
			}
		}
		return index;
	}

	/** How a word is written: "TV", "Monday", "ship". */
	enum Shape {
		ACRONYM, CAPITALIZED, LOWER;

		static Shape of(String raw) {
			String trimmed = raw == null ? "" : raw.trim();
			if (trimmed.length() > 1 && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
					&& !trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
				return ACRONYM;
			}
			return !trimmed.isEmpty() && Character.isUpperCase(trimmed.charAt(0)) ? CAPITALIZED : LOWER;
		}
	}

	/** Consonant skeleton (Double Metaphone, both readings) and a rough vowel-aware spoken form. */
	record Sound(String primary, String alternate, String spoken) {

		private static final DoubleMetaphone METAPHONE = metaphone();

		static Sound of(String word) {
			String letters = word.replaceAll("[^a-z]", "");
			String primary = METAPHONE.doubleMetaphone(letters);
			String alternate = METAPHONE.doubleMetaphone(letters, true);
			return new Sound(primary == null ? "" : primary, alternate == null ? "" : alternate, spoken(letters));
		}

		private static DoubleMetaphone metaphone() {
			DoubleMetaphone metaphone = new DoubleMetaphone();
			metaphone.setMaxCodeLen(10);
			return metaphone;
		}

		/**
		 * English spelling → rough sounds, enough to catch homophones like "see"/"sea" or "knight"/"night". Long vowels
		 * become capitals (I = ee, E = ay, A = eye, O = oh, U = oo, Y = you, W = ow) so a later rule cannot rewrite them.
		 */
		static String spoken(String w) {
			String s = w;
			s = s.replaceAll("^kn", "n").replaceAll("^wr", "r").replaceAll("^gn", "n").replaceAll("^ps", "s")
					.replaceAll("^wh", "w").replaceAll("mb$", "m");
			s = s.replace("tch", "ch").replace("ph", "f").replace("igh", "A").replace("ck", "k")
					.replace("qu", "kw").replace("x", "ks");
			s = s.replaceAll("c(?=[eiy])", "s").replace("c", "k").replace("q", "k");
			// Silent-e lengthens the vowel before it: "rate", "bite", "note", "cute", "these".
			s = s.replaceAll("a([^aeiouy])e$", "E$1").replaceAll("i([^aeiouy])e$", "A$1")
					.replaceAll("o([^aeiouy])e$", "O$1").replaceAll("u([^aeiouy])e$", "Y$1")
					.replaceAll("e([^aeiouy])e$", "I$1");
			s = s.replaceAll("ee|ea|ie", "I").replaceAll("ai|ay|ei|ey", "E").replace("oo", "U")
					.replaceAll("oa|ow$", "O").replaceAll("ou|ow", "W").replaceAll("au|aw", "o");
			s = s.replaceAll("([^aeiouy])e$", "$1").replaceAll("y$", "I");
			return s.replaceAll("(.)\\1+", "$1");
		}
	}

	static Set<String> lower(Collection<String> words) {
		Set<String> result = new HashSet<>();
		words.forEach(w -> result.add(clean(w)));
		return result;
	}
}
