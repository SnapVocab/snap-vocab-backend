package vn.ptit.snapvocab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.enumeration.Alignment;
import vn.ptit.snapvocab.domain.enumeration.SemanticRole;

@Entity
@Table(name = "template_fields")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateField {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(
			fetch = FetchType.LAZY,
			optional = false
	)
	@JoinColumn(
			name = "element_id",
			nullable = false,
			unique = true
	)
	private TemplateElement element;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "schema_attribute_id", nullable = false)
	private SchemaAttribute schemaAttribute;

	@Enumerated(EnumType.STRING)
	@Column(name = "semantic_role", length = 50)
	private SemanticRole semanticRole;

	@Column(name = "field_label")
	private String fieldLabel;

	@Builder.Default
	@Column(name = "hide_if_empty", nullable = false)
	private boolean hideIfEmpty = false;

	@Builder.Default
	@Column(name = "audio_action", nullable = false)
	private boolean audioAction = false;

	@Column(name = "font_size")
	private Integer fontSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "alignment")
	private Alignment alignment;

	@Column(name = "color")
	private String color;
}
