package vn.ptit.snapvocab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.common.BaseTimeEntity;

@Entity
@Table(
		name = "topic_item_attribute_values",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_group_instance_attribute",
						columnNames = {
								"group_instance_id",
								"topic_attribute_id"
						}
				)
		}
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicItemAttributeValue extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_instance_id", nullable = false)
	private TopicItemAttributeGroup groupInstance;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "topic_attribute_id", nullable = false)
	private TopicAttribute attribute;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String value;
}
