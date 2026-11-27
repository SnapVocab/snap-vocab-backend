package vn.ptit.snapvocab.dictionary.domain.dto;

import java.util.List;

/**
 * One row of "Từ của tôi". {@code id} and {@code word} are what the list always returned; {@code meaning} and
 * {@code topics} let the row say what the word means and whether it is already being studied.
 *
 * @param meaning first Vietnamese translation, else first Vietnamese definition, else null
 * @param topics  the learner's own topics that already hold this word
 */
public record MyWordDTO(Long id, String word, String meaning, List<Topic> topics) {

	public record Topic(Long topicId, String topicName, Long collectionId, String collectionName) {
	}
}
