package vn.ptit.snapvocab.vocab.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
		name = "topic_item_attribute_groups",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_item_group_position",
						columnNames = {
								"topic_item_id",
								"group_definition_id",
								"position"
						}
				)
		}
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicItemAttributeGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "topic_item_id", nullable = false)
	private TopicItem item;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_definition_id", nullable = false)
	private SchemaAttributeGroup groupDefinition;

	@Builder.Default
	@Column(nullable = false)
	private short position = 1;

	@OneToMany(
			mappedBy = "groupInstance",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	@Builder.Default
	private List<TopicItemAttributeValue> values = new ArrayList<>();
}

