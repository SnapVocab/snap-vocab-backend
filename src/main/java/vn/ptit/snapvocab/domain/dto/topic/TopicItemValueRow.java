package vn.ptit.snapvocab.domain.dto.topic;

/** One EAV value of a topic item, keyed by attribute id (never by attribute name). */
public record TopicItemValueRow(Long topicItemId, Long attributeId, String value) {
}
