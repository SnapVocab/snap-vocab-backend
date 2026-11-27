package vn.ptit.snapvocab.vocab.domain.dto.topic;

/** Where a word goes when the learner has not chosen a topic (buss_mainflow.md BF-07 step 2). */
public record DefaultTopicDTO(
		Long collectionId,
		String collectionName,
		String collectionTranslation,
		Long topicId,
		String topicName,
		String topicTranslation,
		Long activeTemplateId
) {
}
