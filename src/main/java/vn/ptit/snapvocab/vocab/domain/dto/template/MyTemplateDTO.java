package vn.ptit.snapvocab.vocab.domain.dto.template;

import java.time.Instant;
import java.util.List;

/**
 * One entry of "Mẫu thẻ của tôi". A topic can only show a template of its own schema, so a new topic picking this
 * template is created on {@code schemaId}.
 */
public record MyTemplateDTO(
		Long id,
		Long schemaId,
		String name,
		long usageCount,
		List<String> frontFields,
		List<String> backFields,
		Instant updatedAt
) {
}
