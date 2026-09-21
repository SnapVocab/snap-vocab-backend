package vn.ptit.snapvocab.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.domain.enumeration.TemplateElementType;

@Entity
@Table(
		name = "template_elements",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_template_element_position",
				columnNames = {"template_id", "position"}
		)
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateElement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "template_id", nullable = false)
	private Template template;

	@Column(nullable = false)
	private Integer position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TemplateElementType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "side", length = 10, nullable = false)
	@Builder.Default
	private vn.ptit.snapvocab.domain.enumeration.CardSide side = vn.ptit.snapvocab.domain.enumeration.CardSide.FRONT;

	@Column(name = "section_label", length = 100)
	private String sectionLabel;

	@Column(name = "repeatable")
	@Builder.Default
	private Boolean repeatable = false;

	@OneToOne(
			mappedBy = "element",
			cascade = CascadeType.ALL,
			optional = true
	)
	private TemplateField field;
}
