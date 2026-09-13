package vn.ptit.snapvocab.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

@Entity
@Table(
		name = "schema_attribute_groups",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_schema_group_name",
						columnNames = {"schema_id", "name"}
				)
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaAttributeGroup extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "schema_id", nullable = false)
	private Schema schema;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(length = 255)
	private String label;

	@Builder.Default
	@Column(nullable = false)
	private boolean multiple = false;

	@Column(nullable = false)
	private short position;

	@OneToMany(
			mappedBy = "group",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@OrderBy("position ASC")
	@Builder.Default
	private List<SchemaAttribute> attributes = new ArrayList<>();

	@OneToMany(
			mappedBy = "groupDefinition",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@Builder.Default
	private List<TopicItemAttributeGroup> instances = new ArrayList<>();
}
