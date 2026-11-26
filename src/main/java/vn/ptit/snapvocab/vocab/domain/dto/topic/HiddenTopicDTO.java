package vn.ptit.snapvocab.vocab.domain.dto.topic;

import java.time.Instant;

/** GET /api/admin/topics/hidden: one hidden system topic; {@code learners} counts the topic and its subtopics. */
public record HiddenTopicDTO(Long id, String name, String translation, Long parentId, String parentName, Instant hiddenAt,
		String hiddenBy, String reason, int subtopics, int learners) {
}
