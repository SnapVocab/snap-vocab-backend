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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.common.BaseTimeEntity;
import vn.ptit.snapvocab.domain.enumeration.DataType;

@Entity
@Table(
		name = "schema_attributes",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_group_attribute_name",
						columnNames = {"group_id", "name"}
				)
		}
)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemaAttribute extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private SchemaAttributeGroup group;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(length = 255)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_type", nullable = false, length = 50)
	private DataType dataType;

	@Builder.Default
	@Column(nullable = false)
	private boolean required = false;

	@Column(nullable = false)
	private short position;

	@OneToMany(
			mappedBy = "attribute",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@Builder.Default
	private List<TopicItemAttributeValue> values = new ArrayList<>();
}
