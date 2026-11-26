package vn.ptit.snapvocab.vocab.domain.dto.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import vn.ptit.snapvocab.vocab.domain.enumeration.Alignment;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;

/**
 * PUT /api/admin/templates/{id} (snapAdmin templates_ui.md §8.2): the whole visible element list in display order.
 * {@code position} is ignored (the server numbers 0..n, front side first as sent); an element with {@code id} keeps it.
 * Code, schema and default flag never change here; server-only hidden bindings are kept.
 */
public record UpdateTemplateRequest(
		@NotNull Long version,
		@NotBlank @Size(max = 255) String name,
		@NotEmpty @Size(max = 200) List<@Valid @NotNull Element> elements) {

	public record Element(
			Long id,
			Integer position,
			@NotNull TemplateElementType type,
			@NotNull CardSide side,
			@Size(max = 100) String sectionLabel,
			Boolean repeatable,
			@Valid Field field) {
	}

	public record Field(
			Long id,
			@NotNull Long schemaAttributeId,
			String attributeName,
			SemanticRole semanticRole,
			@Size(max = 255) String fieldLabel,
			boolean hideIfEmpty,
			boolean audioAction,
			@Min(8) @Max(64) Integer fontSize,
			Alignment alignment,
			@Pattern(regexp = "#[0-9A-Fa-f]{6}") String color) {
	}

	/** POST /api/admin/templates/{id}/default. */
	public record DefaultRequest(@NotNull Long version) {
	}
}
